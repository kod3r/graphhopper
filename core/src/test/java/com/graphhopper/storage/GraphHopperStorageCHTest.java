/*
 *  Licensed to GraphHopper and Peter Karich under one or more contributor
 *  license agreements. See the NOTICE file distributed with this work for 
 *  additional information regarding copyright ownership.
 * 
 *  GraphHopper licenses this file to you under the Apache License, 
 *  Version 2.0 (the "License"); you may not use this file except in 
 *  compliance with the License. You may obtain a copy of the License at
 * 
 *       http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package com.graphhopper.storage;

import com.graphhopper.routing.QueryGraph;
import com.graphhopper.routing.ch.PrepareEncoder;
import com.graphhopper.routing.util.*;
import com.graphhopper.storage.index.QueryResult;
import com.graphhopper.util.*;
import com.graphhopper.util.shapes.BBox;
import java.io.IOException;

import static org.junit.Assert.*;

import org.junit.Test;

/**
 * @author Peter Karich
 */
public class GraphHopperStorageCHTest extends GraphHopperStorageTest
{
    protected CHGraph getGraph( GraphHopperStorage ghStorage )
    {
        return ghStorage.getGraph(CHGraph.class);
    }

    @Override
    public GraphHopperStorage newGHStorage( Directory dir, boolean is3D )
    {
        return new GraphHopperStorage(true, dir, encodingManager, is3D, new GraphExtension.NoOpExtension());
    }

    @Test
    public void testCannotBeLoadedWithNormalGraphHopperStorageClass()
    {
        GraphHopperStorage g = newGHStorage(new RAMDirectory(defaultGraphLoc, true), false).create(defaultSize);
        g.flush();
        g.close();

        g = new GraphBuilder(encodingManager).setLocation(defaultGraphLoc).setMmap(false).setStore(true).create();
        try
        {
            g.loadExisting();
            assertTrue(false);
        } catch (Exception ex)
        {
        }

        g = newGHStorage(new RAMDirectory(defaultGraphLoc, true), false);
        assertTrue(g.loadExisting());
        // empty graph still has invalid bounds
        assertEquals(g.getBounds(), BBox.createInverse(false));
    }

    @Test
    public void testPrios()
    {
        GraphHopperStorage storage = createGHStorage();
        CHGraph g = getGraph(storage);
        g.getNodeAccess().ensureNode(30);
        storage.freeze();

        assertEquals(0, g.getLevel(10));

        g.setLevel(10, 100);
        assertEquals(100, g.getLevel(10));

        g.setLevel(30, 100);
        assertEquals(100, g.getLevel(30));
    }

    @Test
    public void testEdgeFilter()
    {
        GraphHopperStorage ghStorage = createGHStorage();
        CHGraph g = getGraph(ghStorage);
        g.edge(0, 1, 10, true);
        g.edge(0, 2, 20, true);
        g.edge(2, 3, 30, true);
        g.edge(10, 11, 1, true);

        ghStorage.freeze();
        EdgeSkipIterState tmpIter = g.shortcut(3, 4);
        tmpIter.setDistance(40).setFlags(carEncoder.setAccess(0, true, true));
        assertEquals(EdgeIterator.NO_EDGE, tmpIter.getSkippedEdge1());
        assertEquals(EdgeIterator.NO_EDGE, tmpIter.getSkippedEdge2());

        g.shortcut(0, 4).setDistance(40).setFlags(carEncoder.setAccess(0, true, true));
        g.setLevel(0, 1);
        g.setLevel(4, 1);

        EdgeIterator iter = g.createEdgeExplorer(new LevelEdgeFilter(g)).setBaseNode(0);
        assertEquals(1, GHUtility.count(iter));
        iter = g.createEdgeExplorer().setBaseNode(2);
        assertEquals(2, GHUtility.count(iter));

        tmpIter = g.shortcut(5, 6);
        tmpIter.setSkippedEdges(1, 2);
        assertEquals(1, tmpIter.getSkippedEdge1());
        assertEquals(2, tmpIter.getSkippedEdge2());
    }

    @Test
    public void testDisconnectEdge()
    {
        GraphHopperStorage ghStorage = createGHStorage();
        CHGraphImpl lg = (CHGraphImpl) getGraph(ghStorage);

        EdgeExplorer chCarOutExplorer = lg.createEdgeExplorer(carOutFilter);
        EdgeExplorer tmpCarInExplorer = lg.createEdgeExplorer(carInFilter);

        EdgeExplorer baseCarOutExplorer = ghStorage.createEdgeExplorer(carOutFilter);

        // only remove edges
        long flags = carEncoder.setProperties(60, true, true);
        long flags2 = carEncoder.setProperties(60, true, false);
        lg.edge(4, 1, 30, true);
        ghStorage.freeze();
        EdgeSkipIterState tmp = lg.shortcut(1, 2);
        tmp.setDistance(10).setFlags(flags);
        tmp.setSkippedEdges(10, 11);
        tmp = lg.shortcut(1, 0);
        tmp.setDistance(20).setFlags(flags2);
        tmp.setSkippedEdges(12, 13);
        tmp = lg.shortcut(3, 1);
        tmp.setDistance(30).setFlags(flags2);
        tmp.setSkippedEdges(14, 15);
        // create everytime a new independent iterator for disconnect method
        EdgeIterator iter = lg.createEdgeExplorer().setBaseNode(1);
        iter.next();
        assertEquals(3, iter.getAdjNode());
        assertEquals(1, GHUtility.count(chCarOutExplorer.setBaseNode(3)));
        lg.disconnect(lg.createEdgeExplorer(), iter);
        assertEquals(0, GHUtility.count(chCarOutExplorer.setBaseNode(3)));
        // no shortcuts visible
        assertEquals(0, GHUtility.count(baseCarOutExplorer.setBaseNode(3)));

        // even directed ways change!
        assertTrue(iter.next());
        assertEquals(0, iter.getAdjNode());
        assertEquals(1, GHUtility.count(tmpCarInExplorer.setBaseNode(0)));
        lg.disconnect(lg.createEdgeExplorer(), iter);
        assertEquals(0, GHUtility.count(tmpCarInExplorer.setBaseNode(0)));

        iter.next();
        assertEquals(2, iter.getAdjNode());
        assertEquals(1, GHUtility.count(chCarOutExplorer.setBaseNode(2)));
        lg.disconnect(lg.createEdgeExplorer(), iter);
        assertEquals(0, GHUtility.count(chCarOutExplorer.setBaseNode(2)));

        assertEquals(GHUtility.asSet(0, 2, 4), GHUtility.getNeighbors(chCarOutExplorer.setBaseNode(1)));
        assertEquals(GHUtility.asSet(4), GHUtility.getNeighbors(baseCarOutExplorer.setBaseNode(1)));
    }

    @Test
    public void testGetWeight()
    {
        GraphHopperStorage ghStorage = createGHStorage();
        CHGraphImpl g = (CHGraphImpl) getGraph(ghStorage);
        assertFalse(g.edge(0, 1).isShortcut());
        assertFalse(g.edge(1, 2).isShortcut());

        ghStorage.freeze();

        // only remove edges
        long flags = carEncoder.setProperties(10, true, true);
        EdgeSkipIterState sc1 = g.shortcut(0, 1);
        assertTrue(sc1.isShortcut());
        sc1.setWeight(2.001);
        assertEquals(2.001, sc1.getWeight(), 1e-3);
        sc1.setWeight(100.123);
        assertEquals(100.123, sc1.getWeight(), 1e-3);
        sc1.setWeight(Double.MAX_VALUE);
        assertTrue(Double.isInfinite(sc1.getWeight()));

        sc1.setFlags(flags);
        sc1.setWeight(100.123);
        assertEquals(100.123, sc1.getWeight(), 1e-3);
        assertTrue(carEncoder.isForward(sc1.getFlags()));
        assertTrue(carEncoder.isBackward(sc1.getFlags()));

        flags = carEncoder.setProperties(10, false, true);
        sc1.setFlags(flags);
        sc1.setWeight(100.123);
        assertEquals(100.123, sc1.getWeight(), 1e-3);
        assertFalse(carEncoder.isForward(sc1.getFlags()));
        assertTrue(carEncoder.isBackward(sc1.getFlags()));
    }

    @Test
    public void testGetWeightIfAdvancedEncoder()
    {
        FlagEncoder customEncoder = new Bike2WeightFlagEncoder();
        GraphHopperStorage ghStorage = new GraphBuilder(new EncodingManager(customEncoder)).setCHGraph(true).create();
        ghStorage.edge(0, 2);
        ghStorage.freeze();

        CHGraphImpl lg = (CHGraphImpl) ghStorage.getGraph(CHGraph.class);
        EdgeSkipIterState sc1 = lg.shortcut(0, 1);
        long flags = customEncoder.setProperties(10, false, true);
        sc1.setFlags(flags);
        sc1.setWeight(100.123);

        assertEquals(100.123, lg.getEdgeProps(sc1.getEdge(), sc1.getAdjNode()).getWeight(), 1e-3);
        assertEquals(100.123, lg.getEdgeProps(sc1.getEdge(), sc1.getBaseNode()).getWeight(), 1e-3);
        assertEquals(100.123, ((EdgeSkipIterState) GHUtility.getEdge(lg, sc1.getBaseNode(), sc1.getAdjNode())).getWeight(), 1e-3);
        assertEquals(100.123, ((EdgeSkipIterState) GHUtility.getEdge(lg, sc1.getAdjNode(), sc1.getBaseNode())).getWeight(), 1e-3);

        sc1 = lg.shortcut(1, 0);
        assertTrue(sc1.isShortcut());
        sc1.setFlags(PrepareEncoder.getScDirMask());
        sc1.setWeight(1.011011);
        assertEquals(1.011011, sc1.getWeight(), 1e-3);
    }

    @Test
    public void testQueryGraph()
    {
        GraphHopperStorage ghStorage = createGHStorage();
        CHGraph chGraph = getGraph(ghStorage);
        NodeAccess na = chGraph.getNodeAccess();
        na.setNode(0, 1.00, 1.00);
        na.setNode(1, 1.02, 1.00);
        na.setNode(2, 1.04, 1.00);

        EdgeIteratorState edge1 = chGraph.edge(0, 1);
        chGraph.edge(1, 2);
        ghStorage.freeze();
        chGraph.shortcut(0, 1);

        QueryGraph qGraph = new QueryGraph(chGraph);
        QueryResult fromRes = createQR(1.004, 1.01, 0, edge1);
        QueryResult toRes = createQR(1.019, 1.00, 0, edge1);
        qGraph.lookup(fromRes, toRes);

        Graph baseGraph = qGraph.getBaseGraph();
        EdgeExplorer explorer = baseGraph.createEdgeExplorer();

        assertTrue(chGraph.getNodes() < qGraph.getNodes());
        assertTrue(baseGraph.getNodes() == qGraph.getNodes());

        // traverse virtual edges and normal edges but no shortcuts!
        assertEquals(GHUtility.asSet(fromRes.getClosestNode()), GHUtility.getNeighbors(explorer.setBaseNode(0)));
        assertEquals(GHUtility.asSet(toRes.getClosestNode(), 2), GHUtility.getNeighbors(explorer.setBaseNode(1)));

        // get neighbors from virtual nodes
        assertEquals(GHUtility.asSet(0, toRes.getClosestNode()), GHUtility.getNeighbors(explorer.setBaseNode(fromRes.getClosestNode())));
        assertEquals(GHUtility.asSet(1, fromRes.getClosestNode()), GHUtility.getNeighbors(explorer.setBaseNode(toRes.getClosestNode())));
    }

    QueryResult createQR( double lat, double lon, int wayIndex, EdgeIteratorState edge )
    {
        QueryResult res = new QueryResult(lat, lon);
        res.setClosestEdge(edge);
        res.setWayIndex(wayIndex);
        res.setSnappedPosition(QueryResult.Position.EDGE);
        res.calcSnappedPoint(Helper.DIST_PLANE);
        return res;
    }

    @Test
    @Override
    public void testSave_and_Freeze() throws IOException
    {
        // belongs to each other
        super.testSave_and_Freeze();

        // test freeze and shortcut creation & loading
        graph = newGHStorage(new RAMDirectory(defaultGraphLoc, true), true).
                create(defaultSize);
        graph.edge(1, 0);
        graph.edge(8, 9);
        graph.freeze();
        CHGraph chGraph = getGraph(graph);

        assertEquals(1, GHUtility.count(graph.createEdgeExplorer().setBaseNode(1)));
        assertEquals(1, GHUtility.count(chGraph.createEdgeExplorer().setBaseNode(1)));

        chGraph.shortcut(2, 3);

        // should be identical to access without shortcut
        assertEquals(1, GHUtility.count(graph.createEdgeExplorer().setBaseNode(1)));
        assertEquals(1, GHUtility.count(chGraph.createEdgeExplorer().setBaseNode(1)));

        // base graph does not see shortcut        
        assertEquals(0, GHUtility.count(graph.createEdgeExplorer().setBaseNode(2)));
        assertEquals(1, GHUtility.count(chGraph.createEdgeExplorer().setBaseNode(2)));

        graph.flush();
        graph.close();

        graph = newGHStorage(new RAMDirectory(defaultGraphLoc, true), true);
        assertTrue(graph.loadExisting());
        assertTrue(graph.isFrozen());

        chGraph = getGraph(graph);
        assertEquals(10, chGraph.getNodes());
        assertEquals(2, graph.getAllEdges().getCount());
        assertEquals(3, chGraph.getAllEdges().getCount());
        assertEquals(1, GHUtility.count(chGraph.createEdgeExplorer().setBaseNode(2)));

        AllEdgesSkipIterator iter = chGraph.getAllEdges();
        assertTrue(iter.next());
        assertFalse(iter.isShortcut());
        assertEquals(0, iter.getEdge());
        
        assertTrue(iter.next());
        assertFalse(iter.isShortcut());
        assertEquals(1, iter.getEdge());
        
        assertTrue(iter.next());
        assertTrue(iter.isShortcut());
        assertEquals(2, iter.getEdge());
        assertFalse(iter.next());
    }

    @Test
    public void testSimpleShortcutCreationAndTraversal()
    {
        graph = createGHStorage();
        graph.edge(1, 3, 10, true);
        graph.edge(3, 4, 10, true);
        graph.freeze();

        CHGraph lg = graph.getGraph(CHGraph.class);
        lg.shortcut(1, 4).setWeight(3).setFlags(carEncoder.setProperties(10, true, true));

        EdgeExplorer vehicleOutExplorer = lg.createEdgeExplorer(new DefaultEdgeFilter(carEncoder, false, true));
        // iteration should result in same nodes even if reusing the iterator
        assertEquals(GHUtility.asSet(3, 4), GHUtility.getNeighbors(vehicleOutExplorer.setBaseNode(1)));
        assertEquals(GHUtility.asSet(3, 4), GHUtility.getNeighbors(vehicleOutExplorer.setBaseNode(1)));
    }
}
