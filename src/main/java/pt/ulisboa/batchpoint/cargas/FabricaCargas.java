package pt.ulisboa.batchpoint.cargas;

public final class FabricaCargas {

    private FabricaCargas() {}

    public static Carga criar(String nome){
        return switch(nome){

            case "sintetica" -> new Sintetica();

            case "mandelbrot" -> new Mandelbrot();


            default -> throw new IllegalArgumentException("Carga desconhecida: " + nome);
        };

    }
}
