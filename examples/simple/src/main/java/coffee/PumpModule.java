package coffee;

import dagger.Module;
import dagger.Provides;

import javax.inject.Named;

@Module(complete = false, library = true)
class PumpModule {
  @Provides
  Pump providePump(Thermosiphon pump) {
    return pump;
  }

  @Provides
  @Named("coffee.isNuclear")
  boolean provideIsNuclear() {
    return true;
  }
}
