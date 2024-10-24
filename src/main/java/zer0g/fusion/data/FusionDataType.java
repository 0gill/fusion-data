package zer0g.fusion.data;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.util.List;

public interface FusionDataType<T extends FusionData>
{
    interface Validator<T extends FusionData>
    {
        static void failed(String err, List<String> errors) throws ValidationException {
            if (errors != null) {
                errors.add(err);
            } else {
                throw new ValidationException(err);
            }
        }

        void validate(T value, FusionValue testParam, List<String> errors) throws ValidationException;

        default String failedMsg(String error) {
            return name() + ": " + (error != null ? error : "failed");
        }

        String name();

        /**
         * Convenience method to throw exception when a validation is not applicable on supplied type.
         *
         * @param type
         */
        default void notApplicableOnType(FusionDataType type) throws UnsupportedOperationException {
            throw new UnsupportedOperationException("Validation N/A on type " + type.name() + ": " + name());
        }
    }

    /**
     * @param algName
     *       name of the validation algorithm
     * @param algParam
     *       the argument to pass along with the data-value to the validation algorithm
     */
    record Validation(String algName, FusionValue algParam)
    {
    }

    class ValidationException extends IllegalArgumentException
    {
        private final List<String> _errors;

        public ValidationException(String message) {
            this(List.of(message));
        }

        public ValidationException(List<String> errors) {
            _errors = errors;
        }

        @Override
        public String getMessage() {
            return _errors.size() == 1 ? _errors.get(0) : _errors.size() + " validation errors!";
        }

        public List<String> getErrors() {
            return _errors;
        }
    }

    /**
     * If the error-collection list provided is not null, add the error message to it and return.<p/> Otherwise, create
     * and throw a validation exception with the supplied message as the sole error message in its list.<p/>
     *
     * @param err
     *       the error message
     * @param collect
     * @throws ValidationException
     */
    static void validationFailed(String err, List<String> collect) throws ValidationException {
        if (collect != null) {
            collect.add(err);
        } else {
            throw new ValidationException(err);
        }
    }

    /**
     * System-wide unique identifier for the type.
     */
    String name();

    default void validate(T value, Validation validation, List<String> errors) {
        validatorFor(validation.algName).validate(value, validation.algParam, errors);
    }

    Validator validatorFor(String algName) throws IllegalArgumentException;

    /**
     * Read fusion-value from the supplied reader in the fusion default "wire-data-format" (JSON).
     *
     * @param wire
     * @return
     * @throws IOException
     *       if there is an i/o error
     * @throws RuntimeException
     *       if the read object does not conform to the data type format
     */
    T read(Reader wire) throws IOException, ArithmeticException, ClassCastException;

    void write(Writer wire, T value) throws IOException;

    /**
     * @return the java-class for instances of this data type
     */
    Class<?> javaDataClass();
}
