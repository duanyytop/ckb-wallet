package transaction;

import java.math.BigInteger;

public class Receiver {
  public String address;
  public BigInteger capacity;

  public Receiver(String address, BigInteger capacity) {
    this.address = address;
    this.capacity = capacity;
  }
}
