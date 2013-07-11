package coffee.api;

import javax.inject.Inject;
import javax.inject.Named;


public abstract class AbstractThermosiphon {
  @Inject
  @Named("coffee.isNuclear")
  boolean isNuclear;

  protected AbstractThermosiphon() {
  }
}
