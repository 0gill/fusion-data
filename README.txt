# fusion-data (Data-Object Java Core of Zer0g corporation's Fusion XXX Platform)

Has your DATA-beans + keys covered.
Bean To/Form JSON.  Annotated field-value constraints (min/max length, regex, etc.)
Init-->Write-->Readonly bean-state transition to support factory-method construction.
Bean-immutability guarantee via readonly end-state and no data-reference leakage.

Example::::::::

@FoType(fieldOrder = {"name", "ID"})
public interface A extends FusionBean {
  String getName();
  A setName(String name);
  
  @FoField(isKey = true, range = "1,20")
  String getID();
  A setID(String ID);
}

var beanFactory = (FusionBeanObjectType<A>) Fusion.fobType(A.class);
assertEquals(A.class, beanFactory.javaDataClass());

A a1 = beanFactory.make();
a1.setName("Davie Jones").setID("0008");
A a2 = beanFactory.make();
a2.setName("Ramta Jogi").setID("0001");
A awho = beanFactory.makeKey();
assertThrows(RuntimeException.class, () -> awho.setName("Perfectly Fine"));
assertThrows(RuntimeException.class, () -> awho.ensureReadonly());
assertThrows(IllegalArgumentException.class, () -> awho.setID(""));
assertDoesNotThrow(() -> awho.setID("0"));
assertDoesNotThrow(() -> awho.ensureReadonly());
assertThrows(IllegalStateException.class, () -> awho.setID("0000"));

:::::::

Enjoy.  My gratitude to the Apache Community.  Camel is all time fav  :-)
