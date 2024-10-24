package zer0g.fusion.data;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public abstract class FusionValueVisitor
{

    abstract void visitNull() throws IOException;

    abstract void visitBool(Boolean value) throws IOException;

    abstract void visitInteger(Number value) throws IOException;

    abstract void visitDecimal(Number value) throws IOException;

    abstract void visitString(Object value) throws IOException;

    abstract void visitDate(LocalDate value) throws IOException;

    abstract void visitTime(LocalTime value) throws IOException;

    abstract void visitDateTime(LocalDateTime value) throws IOException;

    abstract void visitInstant(Instant value) throws IOException;

    abstract void visitDuration(Duration value) throws IOException;

    abstract void visitList(FusionList<?> value) throws IOException;

    abstract void visitMap(FusionMap<?> value) throws IOException;

    abstract void visitEnum(Enum<?> value) throws IOException;

    abstract void visitObject(FusionObject value) throws IOException;

    abstract void visitBlob(Blob value) throws IOException;
}
