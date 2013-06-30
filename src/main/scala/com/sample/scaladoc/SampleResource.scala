package com.sample.scaladoc

/**
 * Fortunes Resource.
 *
 * <code>
 *  Foo = "asdf";
 * </code>
 *
 * @author Joe Betz
 */
class SampleResource {

  /**
   * Get method.
   * @param param1 provides a string
   * @param param2 provides a <b>boolean</b>
   * @return another string
   */
  def get(param1: String, param2: Boolean): String = {
    "test"
  }
}
