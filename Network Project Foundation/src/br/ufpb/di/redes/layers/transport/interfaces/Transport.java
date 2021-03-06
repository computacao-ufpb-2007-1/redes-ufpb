/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package br.ufpb.di.redes.layers.transport.interfaces;

import br.ufpb.di.redes.layers.all.DefaultValues;
import br.ufpb.di.redes.layers.all.InterlayerData;
import br.ufpb.di.redes.layers.all.Layer;
import br.ufpb.di.redes.layers.all.NullLayer;
import br.ufpb.di.redes.layers.network.interfaces.Network;
import java.util.concurrent.ArrayBlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * @author Thiago
 */
public abstract class Transport extends Layer<NullLayer, Network> {

    /**Logger para esta classe*/
    private static final Logger logger = LoggerFactory.getLogger(Transport.class);

    /**Tamanho do buffer de mensagens vindas da camada de baixo*/
    private static int receivedBufferSize = DefaultValues.DOWN_BUFFER_SIZE;

    /**Usado para armazenar mensagens vindas da camada de rede, no buffer*/
    private static class ToReceiveMessage {
        public final InterlayerData data;
        public final int source_ip;
        public ToReceiveMessage(InterlayerData data, int source_ip) {
            this.data = data;
            this.source_ip = source_ip;
        }
    }

    /**Buffer de mensagens recebidas da camada de baixo.*/
    private ArrayBlockingQueue<ToReceiveMessage> receivedBuffer;

    /**Thread que processa mensagens recebidas da camada de baixo.*/
    private Thread receivedThread;

    public Transport(Network downLayer) {
        super(downLayer);
        receivedBuffer = new ArrayBlockingQueue<ToReceiveMessage>(receivedBufferSize);
        attach(new NullLayer());
    }

    @Override
    public void start() {
        super.start();
        logger.info("Iniciando servico");
        receivedThread = new Thread(getName() + ":receiver") {
            @Override
            public void run() {
                try {
                    while (!interrupted()) {
                        ToReceiveMessage tmp = receivedBuffer.take();
                        processReceivedData(tmp.data, tmp.source_ip);
                    }
                } catch (InterruptedException e) {
                }
                logger.debug("Thread de envio interrompida.");
            }
        };

        receivedThread.start();
    }


    /**
     * Armazena data e source_ip, se houver espaco imediatamente. Se nao
     * houver espaco imediatamente, bloqueia ate que haja espaco disponivel.
     * <p/>
     * Esta e' a interface publica que deve ser chamada a partir de camadas
     * inferiores. Em geral, nao deve ser chamado diretamente o metodo
     * bubbleUp() normalmente se encarrega desta tarefa.
     *
     * @param data PDU recebida.
     * @param source_ip Ip que originou o pacote
     *
     * @throws IllegalStateException Sera lancada se nao houver entidade
     * amarrada ao topo desta entidade.
     */
    public void received(InterlayerData data, int source_ip) {
        EntityState state = getState();
        if (state == EntityState.RUNNING) {
            logger.debug("Dados recebidos da rede. Para a fila!");
            try {
                ToReceiveMessage tmp = new ToReceiveMessage(data, source_ip);
                receivedBuffer.put(tmp);
            } catch (InterruptedException e) {
                logger.error("Interrompido.", e);
                Thread.currentThread().interrupt();
            }
        } else {
            logger.warn("Dados recebidos da rede, porem em estado invalido: {}.", state);
            throw new IllegalStateException("Tentando repassar dados, mas " +
                    "a entidade esta em estado invalido: "+state.name());
        }
    }

    /**
     * Repassa data e dest_ip, para a camada de baixo.
     * @param data
     * @throws InterruptedException
     */
    protected void bubbleDown (InterlayerData data, int dest_ip) {
        logger.debug("Repassando mensagem para a camada de rede.");
        downLayer.send(data, dest_ip);
    }

    /**
     * Este metodo e' chamado automaticamente, pela thread de recebimento.
     * <p/>
     * A mensagem deve ser completamente processada e repassada para a camada
     * de cima, se necessário, antes que este metodo retorne.
     */
    protected abstract void processReceivedData(InterlayerData data, int source_ip);

    /**
     * Estabelece conexao com servidor remoto.
     *
     * @param dest_ip
     * @param remote_port
     * @return Um objeto que representa a conexao feita.
     * @throws UnnableToConnectException
     */
    public abstract Connection connect(int dest_ip, int remote_port)
            throws UnnableToConnectException;

    @Override
    protected void finalize() throws Throwable {

        receivedThread.interrupt();

        super.finalize();
    }

    /**
     * Aguarda conexao com o cliente remoto.
     *
     * @param local_port
     * @return
     */
    public abstract Connection listen(int local_port);
    
    protected abstract void close (Connection con);

    protected abstract boolean isActive (Connection con);

    protected abstract void put (Connection con, byte b);


    /**
     * Repassa um byte para a conexao especificada.
     * @param whereTo
     * @param b
     */
    protected void bubbleUp (Connection whereTo, int b) {
        whereTo.bubbleUp(b);
    }

}
