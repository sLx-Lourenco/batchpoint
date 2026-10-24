package pt.ulisboa.batchpoint.comum;

public enum Politica {
    PLANA("plana"),
    HIERARQUICA("hierarquica");

    private final String tipo;

    Politica(String tipo) {
        this.tipo = tipo;
    }

    public String tipo(){
        return tipo;
    }

    
    public static Politica parse(String nome){

        for(Politica x : Politica.values()){
            if(x.tipo().equals(nome)){
                return x;
            }

        }

        throw new IllegalArgumentException("Politica invalida: " + nome);

    }






}
