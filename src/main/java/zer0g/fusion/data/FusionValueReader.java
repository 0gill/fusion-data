package zer0g.fusion.data;

import java.io.IOException;

public interface FusionValueReader
{
    abstract class Base implements FusionValueReader
    {
        @Override
        public FusionValue read(FusionValueType type, FusionValueDomain domain) throws IOException {
            return switch (type) {
                case ANY -> readAny();
                case BOOL -> readBool();
                case INTEGER -> readInteger(domain);
                case DECIMAL -> readDecimal(domain);
                case STRING -> readString(domain);
                case DATE -> readDate(domain);
                case TIME -> readTime(domain);
                case DATETIME -> readDateTime(domain);
                case INSTANT -> readInstant(domain);
                case DURATION -> readDuration(domain);
                case OBJECT -> readObject(domain);
                case LIST -> readList(domain);
                case MAP -> readMap(domain);
                case ENUM -> readEnum(domain);
                case BLOB -> readBlob(domain);
            };
        }

        protected abstract FusionValue readAny() throws IOException;

        protected abstract FusionValue readBool() throws IOException;

        protected abstract FusionValue readInteger(FusionValueDomain domain) throws IOException;

        protected abstract FusionValue readDecimal(FusionValueDomain domain) throws IOException;

        protected abstract FusionValue readString(FusionValueDomain domain) throws IOException;

        protected abstract FusionValue readDate(FusionValueDomain domain) throws IOException;

        protected abstract FusionValue readTime(FusionValueDomain domain) throws IOException;

        protected abstract FusionValue readDateTime(FusionValueDomain domain) throws IOException;

        protected abstract FusionValue readInstant(FusionValueDomain domain) throws IOException;

        protected abstract FusionValue readDuration(FusionValueDomain domain) throws IOException;

        protected abstract FusionValue readObject(FusionValueDomain domain) throws IOException;

        protected abstract FusionValue readList(FusionValueDomain domain) throws IOException;

        protected abstract FusionValue readMap(FusionValueDomain domain) throws IOException;

        protected abstract FusionValue readEnum(FusionValueDomain domain) throws IOException;

        protected abstract FusionValue readBlob(FusionValueDomain domain) throws IOException;
    }

    /**
     * @param type
     *       the type of the expected value
     * @param domain
     *       optional domain of the expected value
     * @return
     * @throws IOException
     */
    FusionValue read(FusionValueType type, FusionValueDomain domain) throws IOException;
}
