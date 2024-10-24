package zer0g.fusion.data;

/**
 * Root interface for all application domain data-object types.  In other words, any application domain data-object, say
 * org.acme.Customer, that wants to *become* a fusion-object should either EXTEND this interface or
 * {@link FusionBeanObject} class. The latter is needed in cases such as when:
 * <ul>
 *     <li>Certain 'set' methods should be protected, because they're called from other user-defined set-methods.
 *     E.g. to support scenarios where multiple fields must be set together.</li>
 *     <li>Computed/transient fields are needed... e.g. a "Map" field that is computed from a "List" field.</li>
 * </ul>
 * <p/>
 */
public non-sealed interface FusionBean extends FusionObject
{
    /**
     * Supports custom validation tests to be performed before the bean transitions out of current state
     * ({@link IwrState#INIT} or {@link IwrState#WRITE}).
     * <p>
     * Implementation should throw exception if transition is not allowed.
     *
     * @param targetState
     *       {@link IwrState#READ} or {@link IwrState#WRITE}
     */
    default void customPrepForIwrStateChange(IwrState targetState) {
    }
}
