package pt.ulisboa.batchpoint.protocolo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

public final class Canal implements Closeable{

    private final Socket socket;
    private final DataOutputStream fluxoSaida;
    private final DataInputStream fluxoEntrada;


    public Canal(Socket socket) throws IOException{
        this.socket=socket;
        socket.setTcpNoDelay(true);

        fluxoSaida = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        fluxoEntrada = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
    }

    @Override 
    public final void close() throws IOException { 
        socket.close(); 
    }

    public void enviarHello(int idNo, int threads) throws  IOException{
        fluxoSaida.writeByte(Protocolo.HELLO);
        fluxoSaida.writeInt(idNo);
        fluxoSaida.writeInt(threads);
        fluxoSaida.flush();
    }

    public void enviarPedido(int maxTarefas) throws  IOException{
        fluxoSaida.writeByte(Protocolo.REQUEST);
        fluxoSaida.writeInt(maxTarefas);
        fluxoSaida.flush();
    }

    public void enviarTarefas(List<Tarefa> tarefas) throws IOException{
        fluxoSaida.writeByte(Protocolo.TASKS);
        fluxoSaida.writeInt(tarefas.size());
        for(Tarefa t : tarefas){
            fluxoSaida.writeInt(t.id());
            fluxoSaida.writeLong(t.parametro());
        }
        fluxoSaida.flush();
    }

    public void enviarResultados(List<Resultado> resultados) throws IOException{
        fluxoSaida.writeByte(Protocolo.RESULTS);
        fluxoSaida.writeInt(resultados.size());
        for(Resultado r : resultados){
            fluxoSaida.writeInt(r.id());
            fluxoSaida.writeLong(r.valor());
            fluxoSaida.writeLong(r.duracaoNs());
        }
        fluxoSaida.flush();
    }

    public void enviarDone() throws IOException{
        fluxoSaida.writeByte(Protocolo.DONE);
        fluxoSaida.flush();
    }


    public byte lerTipo() throws IOException{
        return fluxoEntrada.readByte();   
    }

    public int lerInt() throws IOException{
        return fluxoEntrada.readInt();   
    }

    public List<Tarefa> lerTarefas() throws IOException{
        int n = fluxoEntrada.readInt();

        List<Tarefa> tarefas = new ArrayList<>(n);
        
        for(int i=0; i<n; i++){
            int id = fluxoEntrada.readInt();
            long parametro = fluxoEntrada.readLong();
            tarefas.add(new Tarefa(id, parametro));
        }
        return tarefas;
    }

    public List<Resultado> lerResultados() throws IOException{
        int n = fluxoEntrada.readInt();

        List<Resultado> resultados = new ArrayList<>(n);
        
        for(int i=0; i<n; i++){
            int id = fluxoEntrada.readInt();
            long valor = fluxoEntrada.readLong();
            long duracaoNs = fluxoEntrada.readLong();
            resultados.add(new Resultado(id, valor, duracaoNs));
        }
        return resultados;
    }    

    public int bytesEnviados(){
        return fluxoSaida.size();
    }
}
