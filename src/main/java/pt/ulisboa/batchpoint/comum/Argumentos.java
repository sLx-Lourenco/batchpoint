package pt.ulisboa.batchpoint.comum;

import java.net.InetSocketAddress;
import java.util.HashMap;
import java.util.Map;

//leitura de --nome valor
public class Argumentos {

    private final Map<String, String> map = new HashMap<>();

    public Argumentos(String[] args) {
        for (int i = 0; i < args.length; i = i + 2) {
            String parametro = args[i];
            if (!parametro.startsWith("--")) {
                throw new IllegalArgumentException("Argumento " + parametro + " invalido");
            }

            if (i + 1 == args.length) {
                throw new IllegalArgumentException("Argumento " + parametro + " sem valor");
            }

            String nome = parametro.substring(2);
            if (map.containsKey(nome)) {
                throw new IllegalArgumentException("Argumento " + parametro + " repetido");
            }
            map.put(nome, args[i + 1]);        }
    }


    public String texto(String nome) {
        String valor = map.get(nome);
        if (valor == null) {
            throw new IllegalArgumentException("Falta o argumento --" + nome);
        }
        return valor;
    }

    public String texto(String nome, String omissao) {
        return map.getOrDefault(nome, omissao);
    }

    public int inteiro(String nome, int omissao) {
        String valor = map.get(nome);
        if (valor == null) {
            return omissao;
        }
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + nome + " tem de ser um inteiro, veio: " + valor);
        }
    }


    public int inteiro(String nome) {
        String valor = texto(nome);
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + nome + " tem de ser um inteiro, veio: " + valor);
        }
    }

    public long longo(String nome) {
        String valor = texto(nome);
        try {
            return Long.parseLong(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + nome + " tem de ser um número, veio: " + valor);
        }
    }

    public InetSocketAddress endereco(String nome) {
        String valor = texto(nome);
        int doisPontos = valor.lastIndexOf(':');
        if (doisPontos <= 0 || doisPontos == valor.length() - 1) {
            throw new IllegalArgumentException("--" + nome + " tem de ser ip:porta, veio: " + valor);
        }
        String host = valor.substring(0, doisPontos);
        int porta;
        try {
            porta = Integer.parseInt(valor.substring(doisPontos + 1));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("--" + nome + ": porta inválida em " + valor);
        }
        if (porta < 1 || porta > 65535) {
            throw new IllegalArgumentException("--" + nome + ": porta fora de 1-65535 em " + valor);
        }
        return InetSocketAddress.createUnresolved(host, porta);
    }
}
