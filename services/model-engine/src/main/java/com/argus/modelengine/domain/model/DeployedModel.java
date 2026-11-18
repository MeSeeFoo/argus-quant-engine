package com.argus.modelengine.domain.model;

import com.argus.modelengine.domain.instrument.Instrument;
import com.argus.modelengine.domain.instrument.Instruments;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A model bound to its instruments, as the engine runs it (ADR 0014): each input the model
 * declared, joined with the instrument the deployment chose. The same model may be deployed several
 * times, each a {@code DeployedModel} of its own. Created at startup with {@link #deploy}.
 * Immutable.
 *
 * @param model the model to evaluate
 * @param inputs its inputs, in the order the model declared them, at least one
 */
public record DeployedModel(Model model, List<DeployedInput> inputs) {

  /**
   * Copies the inputs, so the record stays immutable.
   *
   * @throws IllegalArgumentException if there are no inputs
   */
  public DeployedModel {
    inputs = List.copyOf(inputs);
    if (inputs.isEmpty()) {
      throw new IllegalArgumentException("a deployed model needs at least one input");
    }
  }

  /**
   * Deploys a model on instruments: joins each input the model declared with the instrument the
   * deployment chose for it. Startup only.
   *
   * @param model the model
   * @param instruments the deployment's choice: the instrument of each input, by input name
   * @param engineInstruments the instruments the engine handles, which number them
   * @return the deployed model
   * @throws IllegalArgumentException if an input has no instrument, a name is not an input of the
   *     model, or an instrument is not one the engine handles
   */
  public static DeployedModel deploy(
      Model model, Map<String, Instrument> instruments, Instruments engineInstruments) {
    List<DeployedInput> deployed = new ArrayList<>();
    for (InputDefinition input : model.modelConfiguration().inputs()) {
      Instrument instrument = instruments.get(input.name());
      if (instrument == null) {
        throw new IllegalArgumentException("no instrument for input '" + input.name() + "'");
      }
      int instrumentId = engineInstruments.indexOf(instrument);
      if (instrumentId == Instruments.UNKNOWN) {
        throw new IllegalArgumentException(
            "input '"
                + input.name()
                + "' reads "
                + instrument
                + ", which the engine does not handle");
      }
      deployed.add(new DeployedInput(input, instrument, instrumentId));
    }
    if (instruments.size() != deployed.size()) {
      throw new IllegalArgumentException(
          "instruments given for inputs the model does not declare: " + instruments.keySet());
    }
    return new DeployedModel(model, deployed);
  }
}
