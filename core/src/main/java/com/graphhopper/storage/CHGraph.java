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

import com.graphhopper.routing.util.AllEdgesSkipIterator;
import com.graphhopper.routing.util.EdgeFilter;
import com.graphhopper.util.EdgeSkipExplorer;
import com.graphhopper.util.EdgeSkipIterState;

/**
 * Extended graph interface which supports Contraction Hierarchies. Ie. storing and retrieving the
 * levels for a node and creating shortcuts, which are additional 'artificial' edges to speedup
 * traversal in certain cases.
 * <p/>
 * @author Peter Karich
 */
public interface CHGraph extends Graph
{
    /**
     * This methods sets the level of the specified node.
     */
    void setLevel( int nodeId, int level );

    /**
     * @return the level of the specified node.
     */
    int getLevel( int nodeId );

    boolean isShortcut( int edgeId );

    /**
     * This method creates a shortcut between a to b which is nearly identical to creating an edge
     * except that it can be excluded or included for certain traversals or algorithms.
     */
    EdgeSkipIterState shortcut( int a, int b );

    @Override
    EdgeSkipIterState getEdgeProps( int edgeId, int endNode );

    @Override
    EdgeSkipExplorer createEdgeExplorer();

    @Override
    EdgeSkipExplorer createEdgeExplorer( EdgeFilter filter );

    @Override
    AllEdgesSkipIterator getAllEdges();
}
