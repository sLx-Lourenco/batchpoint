package pt.ulisboa.batchpoint.coordenador;

import java.util.List;
import pt.ulisboa.batchpoint.protocolo.Tarefa;

public record Entrega(Repeticao rep, List<Tarefa> listaTarefas) {}
