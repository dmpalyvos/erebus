package io.palyvos.provenance.missing.predicate;

import io.palyvos.provenance.missing.util.Path;
import java.util.Arrays;
import org.apache.commons.lang3.Validate;

public class RenamingHelper {

  static VariableRenaming testInstance(String variable) {
    Validate.notEmpty(variable);
    return new VariableRenaming(variable, Path.empty(), Path.empty());
  }

  static VariableRenaming testInstance(String variable, String pathStr) {
    Validate.notEmpty(variable);
    Validate.notNull(pathStr);
    return new VariableRenaming(variable, Path.empty(),
        Path.of(Arrays.asList(pathStr.split("->"))));
  }

  static VariableRenaming testInstance(String variable, String transformStr, String pathStr) {
    Validate.notEmpty(variable);
    Validate.notEmpty(pathStr);
    Validate.notEmpty(transformStr);
    return new VariableRenaming(variable, Arrays.asList(transformStr.split("->")),
        Arrays.asList(pathStr.split("->")));
  }

  private RenamingHelper() {

  }

}
