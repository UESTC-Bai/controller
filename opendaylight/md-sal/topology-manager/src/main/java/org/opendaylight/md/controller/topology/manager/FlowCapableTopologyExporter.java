/*
 * Copyright (c) 2014 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.md.controller.topology.manager;

import static org.opendaylight.md.controller.topology.manager.FlowCapableNodeMapping.getNodeConnectorKey;
import static org.opendaylight.md.controller.topology.manager.FlowCapableNodeMapping.getNodeKey;
import static org.opendaylight.md.controller.topology.manager.FlowCapableNodeMapping.toTerminationPoint;
import static org.opendaylight.md.controller.topology.manager.FlowCapableNodeMapping.toTerminationPointId;
import static org.opendaylight.md.controller.topology.manager.FlowCapableNodeMapping.toTopologyLink;
import static org.opendaylight.md.controller.topology.manager.FlowCapableNodeMapping.toTopologyNode;
import static org.opendaylight.md.controller.topology.manager.FlowCapableNodeMapping.toTopologyNodeId;

import java.util.concurrent.Future;

import org.opendaylight.controller.md.sal.binding.util.TypeSafeDataReader;
import org.opendaylight.controller.md.sal.common.api.TransactionStatus;
import org.opendaylight.controller.sal.binding.api.data.DataModificationTransaction;
import org.opendaylight.controller.sal.binding.api.data.DataProviderService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeConnectorUpdated;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.inventory.rev130819.FlowCapableNodeUpdated;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.FlowTopologyDiscoveryListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.LinkDiscovered;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.LinkOverutilized;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.LinkRemoved;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.topology.discovery.rev130819.LinkUtilizationNormal;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorRemoved;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeConnectorUpdated;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRemoved;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeUpdated;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.OpendaylightInventoryListener;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.node.NodeConnectorKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NetworkTopology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.NodeId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TopologyId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.TpId;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.Topology;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyBuilder;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.TopologyKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Link;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.Node;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.NodeKey;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPoint;
import org.opendaylight.yang.gen.v1.urn.tbd.params.xml.ns.yang.network.topology.rev131021.network.topology.topology.node.TerminationPointKey;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;

class FlowCapableTopologyExporter implements FlowTopologyDiscoveryListener, OpendaylightInventoryListener {
    protected final static Logger LOG = LoggerFactory.getLogger(FlowCapableTopologyExporter.class);
    public static final TopologyKey TOPOLOGY = new TopologyKey(new TopologyId("flow:1"));

    // FIXME: Flow capable topology exporter should use transaction chaining API
    private DataProviderService dataService;

    public DataProviderService getDataService() {
        return dataService;
    }

    public void setDataService(final DataProviderService dataService) {
        this.dataService = dataService;
    }

    private InstanceIdentifier<Topology> topologyPath;

    public void start() {
        TopologyBuilder tb = new TopologyBuilder();
        tb.setKey(TOPOLOGY);
        topologyPath = InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, TOPOLOGY).build();
        Topology top = tb.build();
        DataModificationTransaction tx = dataService.beginTransaction();
        tx.putOperationalData(topologyPath, top);
        listenOnTransactionState(tx.getIdentifier(),tx.commit());
    }

    @Override
    public void onNodeRemoved(final NodeRemoved notification) {
        NodeId nodeId = toTopologyNodeId(getNodeKey(notification.getNodeRef()).getId());
        InstanceIdentifier<Node> nodeInstance = toNodeIdentifier(notification.getNodeRef());

        synchronized (this) {
            DataModificationTransaction tx = dataService.beginTransaction();
            tx.removeOperationalData(nodeInstance);
            removeAffectedLinks(tx, nodeId);
            listenOnTransactionState(tx.getIdentifier(),tx.commit());
        }
    }

    @Override
    public void onNodeUpdated(final NodeUpdated notification) {
        FlowCapableNodeUpdated fcnu = notification.getAugmentation(FlowCapableNodeUpdated.class);
        if (fcnu != null) {
            Node node = toTopologyNode(toTopologyNodeId(notification.getId()), notification.getNodeRef());
            InstanceIdentifier<Node> path = getNodePath(toTopologyNodeId(notification.getId()));

            synchronized (this) {
                DataModificationTransaction tx = dataService.beginTransaction();
                tx.putOperationalData(path, node);
                listenOnTransactionState(tx.getIdentifier(),tx.commit());
            }
        }
    }

    @Override
    public void onNodeConnectorRemoved(final NodeConnectorRemoved notification) {
        InstanceIdentifier<TerminationPoint> tpInstance = toTerminationPointIdentifier(notification
                .getNodeConnectorRef());
        TpId tpId = toTerminationPointId(getNodeConnectorKey(notification.getNodeConnectorRef()).getId());

        synchronized (this) {
            DataModificationTransaction tx = dataService.beginTransaction();
            tx.removeOperationalData(tpInstance);
            removeAffectedLinks(tx, tpId);
            listenOnTransactionState(tx.getIdentifier(),tx.commit());
        }
    }

    @Override
    public void onNodeConnectorUpdated(final NodeConnectorUpdated notification) {
        FlowCapableNodeConnectorUpdated fcncu = notification.getAugmentation(FlowCapableNodeConnectorUpdated.class);
        if (fcncu != null) {
            NodeId nodeId = toTopologyNodeId(getNodeKey(notification.getNodeConnectorRef()).getId());
            TerminationPoint point = toTerminationPoint(toTerminationPointId(notification.getId()),
                    notification.getNodeConnectorRef());
            InstanceIdentifier<TerminationPoint> path = tpPath(nodeId, point.getKey().getTpId());

            synchronized (this) {
                DataModificationTransaction tx = dataService.beginTransaction();
                tx.putOperationalData(path, point);
                if ((fcncu.getState() != null && fcncu.getState().isLinkDown())
                        || (fcncu.getConfiguration() != null && fcncu.getConfiguration().isPORTDOWN())) {
                    removeAffectedLinks(tx, point.getTpId());
                }
                listenOnTransactionState(tx.getIdentifier(),tx.commit());
            }
        }
    }

    @Override
    public void onLinkDiscovered(final LinkDiscovered notification) {
        Link link = toTopologyLink(notification);
        InstanceIdentifier<Link> path = linkPath(link);

        synchronized (this) {
            DataModificationTransaction tx = dataService.beginTransaction();
            tx.putOperationalData(path, link);
            listenOnTransactionState(tx.getIdentifier(),tx.commit());
        }
    }

    @Override
    public void onLinkOverutilized(final LinkOverutilized notification) {
        // NOOP
    }

    @Override
    public void onLinkRemoved(final LinkRemoved notification) {
        InstanceIdentifier<Link> path = linkPath(toTopologyLink(notification));

        synchronized (this) {
            DataModificationTransaction tx = dataService.beginTransaction();
            tx.removeOperationalData(path);
            listenOnTransactionState(tx.getIdentifier(),tx.commit());
        }
    }

    @Override
    public void onLinkUtilizationNormal(final LinkUtilizationNormal notification) {
        // NOOP
    }

    private static InstanceIdentifier<Node> toNodeIdentifier(final NodeRef ref) {
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey invNodeKey = getNodeKey(ref);

        NodeKey nodeKey = new NodeKey(toTopologyNodeId(invNodeKey.getId()));
        return InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, TOPOLOGY)
                .child(Node.class, nodeKey).build();
    }

    private static InstanceIdentifier<TerminationPoint> toTerminationPointIdentifier(final NodeConnectorRef ref) {
        org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey invNodeKey = getNodeKey(ref);
        NodeConnectorKey invNodeConnectorKey = getNodeConnectorKey(ref);
        return tpPath(toTopologyNodeId(invNodeKey.getId()), toTerminationPointId(invNodeConnectorKey.getId()));
    }

    private void removeAffectedLinks(final DataModificationTransaction transaction, final NodeId id) {
        TypeSafeDataReader reader = TypeSafeDataReader.forReader(transaction);

        Topology topologyData = reader.readOperationalData(topologyPath);
        if (topologyData == null) {
            return;
        }
        for (Link link : topologyData.getLink()) {
            if (id.equals(link.getSource().getSourceNode()) || id.equals(link.getDestination().getDestNode())) {
                InstanceIdentifier<Link> path = topologyPath.child(Link.class, link.getKey());
                transaction.removeOperationalData(path);
            }
        }
    }

    private void removeAffectedLinks(final DataModificationTransaction transaction, final TpId id) {
        TypeSafeDataReader reader = TypeSafeDataReader.forReader(transaction);
        Topology topologyData = reader.readOperationalData(topologyPath);
        if (topologyData == null) {
            return;
        }
        for (Link link : topologyData.getLink()) {
            if (id.equals(link.getSource().getSourceTp()) || id.equals(link.getDestination().getDestTp())) {
                InstanceIdentifier<Link> path = topologyPath.child(Link.class, link.getKey());
                transaction.removeOperationalData(path);
            }
        }
    }

    private static InstanceIdentifier<Node> getNodePath(final NodeId nodeId) {
        NodeKey nodeKey = new NodeKey(nodeId);
        return InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, TOPOLOGY)
                .child(Node.class, nodeKey).build();
    }

    private static InstanceIdentifier<TerminationPoint> tpPath(final NodeId nodeId, final TpId tpId) {
        NodeKey nodeKey = new NodeKey(nodeId);
        TerminationPointKey tpKey = new TerminationPointKey(tpId);
        return InstanceIdentifier.builder(NetworkTopology.class).child(Topology.class, TOPOLOGY)
                .child(Node.class, nodeKey).child(TerminationPoint.class, tpKey).build();
    }

    private static InstanceIdentifier<Link> linkPath(final Link link) {
        InstanceIdentifier<Link> linkInstanceId = InstanceIdentifier.builder(NetworkTopology.class)
                .child(Topology.class, TOPOLOGY).child(Link.class, link.getKey()).build();
        return linkInstanceId;
    }

    /**
     * @param txId transaction identificator
     * @param future transaction result
     */
    private static void listenOnTransactionState(final Object txId, final Future<RpcResult<TransactionStatus>> future) {
        Futures.addCallback(JdkFutureAdapters.listenInPoolThread(future),new FutureCallback<RpcResult<TransactionStatus>>() {
            @Override
            public void onFailure(final Throwable t) {
                LOG.error("Topology export failed for Tx:{}", txId, t);
            }

            @Override
            public void onSuccess(final RpcResult<TransactionStatus> result) {
                if(!result.isSuccessful()) {
                    LOG.error("Topology export failed for Tx:{}", txId);
                }
            }
        });
    }
}
