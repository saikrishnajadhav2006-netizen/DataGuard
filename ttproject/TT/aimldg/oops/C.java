
class A {

    private String pass = "1234567";

    String getPass() {
        return this.pass;

    }
}

public class C {

    String x = "🍏🍎";

    public static void main(String[] args) {
        System.out.println("\n\t acesssing \n");
        A x = new A();
        System.out.println(new C().x);

        System.out.println("\n\t data : " + x.getPass());
    }

}
