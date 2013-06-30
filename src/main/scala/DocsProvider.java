import java.lang.reflect.Method;


public interface DocsProvider
{
  /**
   * @param resourceClass resource class
   * @return class JavaDoc
   */
  String getClassDoc(Class<?> resourceClass);

  /**
   * @param method resource {@link java.lang.reflect.Method}
   * @return method JavaDoc
   */
  String getMethodDoc(Method method);

  /**
   * @param method resource {@link Method}
   * @param name method param name
   * @return method param JavaDoc
   */
  String getParamDoc(Method method, String name);
}