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
package com.graphhopper.routing;

import com.graphhopper.routing.util.*;
import com.graphhopper.storage.Graph;
import com.graphhopper.storage.GraphBuilder;
import com.graphhopper.storage.GraphHopperStorage;
import com.graphhopper.util.EdgeIteratorState;
import com.graphhopper.util.Helper;

import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * @author Peter Karich
 */
@RunWith(Parameterized.class)
public class DijkstraOneToManyTest extends AbstractRoutingAlgorithmTester
{
    /**
     * Runs the same test with each of the supported traversal modes
     */
    @Parameters(name = "{0}")
    public static Collection<Object[]> configs()
    {
        return Arrays.asList(new Object[][]
        {
            {
                TraversalMode.NODE_BASED
            },
//            TODO { TraversalMode.EDGE_BASED_1DIR },
//            TODO { TraversalMode.EDGE_BASED_2DIR },
//            TODO { TraversalMode.EDGE_BASED_2DIR_UTURN }
        });
    }

    private final TraversalMode traversalMode;

    public DijkstraOneToManyTest( TraversalMode tMode )
    {
        this.traversalMode = tMode;
    }

    @Override
    public RoutingAlgorithmFactory createFactory( GraphHopperStorage prepareGraph, AlgorithmOptions prepareOpts )
    {
        return new RoutingAlgorithmFactory()
        {
            @Override
            public RoutingAlgorithm createAlgo( Graph g, AlgorithmOptions opts )
            {
                return new DijkstraOneToMany(g, opts.getFlagEncoder(), opts.getWeighting(), traversalMode);
            }
        };
    }

    @Override
    public void testViaEdges_BiGraph()
    {
        // calcPath with QueryResult not supported
    }

    @Override
    public void testViaEdges_SpecialCases()
    {
        // calcPath with QueryResult not supported
    }

    @Override
    public void testViaEdges_FromEqualsTo()
    {
        // calcPath with QueryResult not supported
    }

    @Override
    public void testViaEdges_WithCoordinates()
    {
        // calcPath with QueryResult not supported
    }

    @Override
    public void testQueryGraphAndFastest()
    {
        // calcPath with QueryResult not supported
    }

    @Override
    public void testTwoWeightsPerEdge2()
    {
        // calcPath with QueryResult not supported
    }

    @Test
    public void testIssue182()
    {
        GraphHopperStorage storage = createGHStorage(false);
        initGraph(storage);
        RoutingAlgorithm algo = createAlgo(storage);
        Path p = algo.calcPath(0, 8);
        assertEquals(Helper.createTList(0, 7, 8), p.calcNodes());

        // expand SPT
        p = algo.calcPath(0, 10);
        assertEquals(Helper.createTList(0, 1, 2, 3, 4, 10), p.calcNodes());
    }

    @Test
    public void testIssue239_and362()
    {
        GraphHopperStorage g = createGHStorage(false);
        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 0, 1, true);

        g.edge(4, 5, 1, true);
        g.edge(5, 6, 1, true);
        g.edge(6, 4, 1, true);

        DijkstraOneToMany algo = (DijkstraOneToMany) createAlgo(g);
        assertEquals(-1, algo.findEndNode(0, 4));
        assertEquals(-1, algo.findEndNode(0, 4));

        assertEquals(1, algo.findEndNode(0, 1));
        assertEquals(1, algo.findEndNode(0, 1));
    }

    @Test
    public void testUseCache()
    {
        RoutingAlgorithm algo = createAlgo(createTestStorage());
        Path p = algo.calcPath(0, 4);
        assertEquals(Helper.createTList(0, 4), p.calcNodes());

        // expand SPT
        p = algo.calcPath(0, 7);
        assertEquals(Helper.createTList(0, 4, 5, 7), p.calcNodes());

        // use SPT
        p = algo.calcPath(0, 2);
        assertEquals(Helper.createTList(0, 1, 2), p.calcNodes());
    }

    @Test
    public void testDifferentEdgeFilter()
    {
        GraphHopperStorage g = new GraphBuilder(encodingManager).setCHGraph(true).create();
        g.edge(4, 3, 10, true);
        g.edge(3, 6, 10, true);

        g.edge(4, 5, 10, true);
        g.edge(5, 6, 10, true);

        DijkstraOneToMany algo = (DijkstraOneToMany) createAlgo(g);
        algo.setEdgeFilter(new EdgeFilter()
        {
            @Override
            public boolean accept( EdgeIteratorState iter )
            {
                return iter.getAdjNode() != 5;
            }
        });
        Path p = algo.calcPath(4, 6);
        assertEquals(Helper.createTList(4, 3, 6), p.calcNodes());

        // important call!
        algo.clear();
        algo.setEdgeFilter(new EdgeFilter()
        {
            @Override
            public boolean accept( EdgeIteratorState iter )
            {
                return iter.getAdjNode() != 3;
            }
        });
        p = algo.calcPath(4, 6);
        assertEquals(Helper.createTList(4, 5, 6), p.calcNodes());
    }

    private Graph initGraph( Graph g )
    {
        // 0-1-2-3-4
        // |       /
        // 7-10----
        // \-8
        g.edge(0, 1, 1, true);
        g.edge(1, 2, 1, true);
        g.edge(2, 3, 1, true);
        g.edge(3, 4, 1, true);
        g.edge(4, 10, 1, true);

        g.edge(0, 7, 1, true);
        g.edge(7, 8, 1, true);
        g.edge(7, 10, 10, true);
        return g;
    }
}
