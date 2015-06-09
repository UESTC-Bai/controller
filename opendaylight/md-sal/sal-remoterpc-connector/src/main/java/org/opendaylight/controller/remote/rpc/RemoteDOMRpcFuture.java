/*
 * Copyright (c) 2015 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.controller.remote.rpc;

import akka.dispatch.OnComplete;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.AbstractFuture;
import com.google.common.util.concurrent.CheckedFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.opendaylight.controller.cluster.datastore.node.utils.serialization.NormalizedNodeSerializer;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcException;
import org.opendaylight.controller.md.sal.dom.api.DOMRpcResult;
import org.opendaylight.controller.md.sal.dom.spi.DefaultDOMRpcResult;
import org.opendaylight.controller.remote.rpc.messages.RpcResponse;
import org.opendaylight.yangtools.yang.data.api.schema.NormalizedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

class RemoteDOMRpcFuture extends AbstractFuture<DOMRpcResult> implements CheckedFuture<DOMRpcResult, DOMRpcException> {

    private static final Logger LOG = LoggerFactory.getLogger(RemoteDOMRpcFuture.class);

    private RemoteDOMRpcFuture(final Future<Object> future) {
        future.onComplete(new FutureUpdater(), ExecutionContext.Implicits$.MODULE$.global());
    }

    public static CheckedFuture<DOMRpcResult, DOMRpcException> from(final Future<Object> future) {
        return new RemoteDOMRpcFuture(future);
    }

    @Override
    public DOMRpcResult checkedGet() throws DOMRpcException {
        try {
            return get();
        } catch (final ExecutionException e) {
            throw mapException(e);
        } catch (final InterruptedException e) {
            throw Throwables.propagate(e);
        }
    }

    @Override
    public DOMRpcResult checkedGet(final long timeout, final TimeUnit unit) throws TimeoutException, DOMRpcException {
        try {
            return get(timeout, unit);
        } catch (final ExecutionException e) {
            throw mapException(e);
        } catch (final InterruptedException e) {
            throw Throwables.propagate(e);
        }
    }

    private DOMRpcException mapException(final ExecutionException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof DOMRpcException) {
            return (DOMRpcException) cause;
        }
        return new RemoteDOMRpcException("Exception during invoking RPC", e);
    }

    private final class FutureUpdater extends OnComplete<Object> {

        @Override
        public void onComplete(final Throwable error, final Object reply) throws Throwable {
            if (error != null) {
                RemoteDOMRpcFuture.this.setException(error);
            } else if (reply instanceof RpcResponse) {
                final RpcResponse rpcReply = (RpcResponse) reply;
                final NormalizedNode<?, ?> result;
                if (rpcReply.getResultNormalizedNode() == null) {
                    result = null;
                    LOG.debug("Received response for invoke rpc: result is null");
                } else {
                    result = NormalizedNodeSerializer.deSerialize(rpcReply.getResultNormalizedNode());
                    LOG.debug("Received response for invoke rpc: result is {}", result);
                }
                RemoteDOMRpcFuture.this.set(new DefaultDOMRpcResult(result));
            }
            RemoteDOMRpcFuture.this.setException(new IllegalStateException("Incorrect reply type " + reply
                    + "from Akka"));
        }
    }

}
