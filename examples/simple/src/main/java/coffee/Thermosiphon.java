package coffee;

import coffee.api.AbstractThermosiphon;

import javax.inject.Inject;

class Thermosiphon extends AbstractThermosiphon implements Pump {
  private final Heater heater;

  @Inject
  Thermosiphon(Heater heater) {
    super();
    this.heater = heater;
  }

  @Override public void pump() {
    if (heater.isHot()) {
      System.out.println("=> => pumping => =>");
    }
  }
}
