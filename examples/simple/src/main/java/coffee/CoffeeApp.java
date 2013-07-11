package coffee;

import javax.inject.Inject;

import dagger.ObjectGraph;

public class CoffeeApp implements Runnable {
  @Inject CoffeeMaker coffeeMaker;

  @Override public void run() {
    coffeeMaker.brew();
  }

  public static void main(String[] args) {
    ObjectGraph objectGraph = ObjectGraph.create(new DripCoffeeModule());
   // Pump coffeeApp = objectGraph.get(Pump.class);
    CoffeeApp coffeeApp = objectGraph.get(CoffeeApp.class);
   // coffeeApp.run();
  }
}
