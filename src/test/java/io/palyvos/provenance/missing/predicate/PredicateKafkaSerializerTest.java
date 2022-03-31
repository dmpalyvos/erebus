package io.palyvos.provenance.missing.predicate;

import io.palyvos.provenance.missing.predicate.Predicate.PredicateStrategy;
import org.testng.Assert;
import org.testng.annotations.Test;

public class PredicateKafkaSerializerTest {

  @Test
  public void testSerializeDeserialize() {
    Predicate predicate = Predicate.of(PredicateStrategy.AND,
        new OneVariableCondition(ReflectionVariable.fromField("a"), var -> var.asLong() > 5));
    PredicateKafkaSerializer serializer = new PredicateKafkaSerializer();
    byte[] data = serializer.serialize(null, predicate);
    Predicate deserialied = serializer.deserialize(null, data);
    Assert.assertEquals(deserialied.uid(), predicate.uid(),
        "Deserialized predicate differs from original!");
  }
}