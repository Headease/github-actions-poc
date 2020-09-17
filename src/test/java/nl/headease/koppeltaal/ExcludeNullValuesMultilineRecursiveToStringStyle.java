package nl.headease.koppeltaal;

import org.apache.commons.lang3.ClassUtils;
import org.apache.commons.lang3.builder.MultilineRecursiveToStringStyle;
import org.apache.commons.lang3.builder.ReflectionToStringBuilder;

/**
 * This class is a customized version of the {@link MultilineRecursiveToStringStyle} class that it extends. It will
 * always set the excludeNullValues of the {@link ReflectionToStringBuilder#toString} to TRUE, which is how we want
 * to use it in {@link nl.koppeltaal.api.KoppeltaalClientTest}.
 *
 * The need for this class stems from the fact that {@link MultilineRecursiveToStringStyle} does not take into account
 * the original settings of the parent {@link ReflectionToStringBuilder#toString} when instantiating new builders for
 * nested objects.
 */
public class ExcludeNullValuesMultilineRecursiveToStringStyle extends MultilineRecursiveToStringStyle {

    /**
     * Required for serialization support.
     * @see java.io.Serializable
     */
    private static final long serialVersionUID = 1L;

    /** Indenting of inner lines. */
    private static final int INDENT = 2;

    /** Current indenting. */
    private int spaces = 2;

    /**
     * Constructor.
     * @param b
     * @param b1
     * @param b2
     * @param o
     */
    public ExcludeNullValuesMultilineRecursiveToStringStyle() {
        super();
    }

    /**
     * Resets the fields responsible for the line breaks and indenting.
     * Must be invoked after changing the {@link #spaces} value.
     */
    private void resetIndent() {
        setArrayStart("{" + System.lineSeparator() + spacer(spaces));
        setArraySeparator("," + System.lineSeparator() + spacer(spaces));
        setArrayEnd(System.lineSeparator() + spacer(spaces - INDENT) + "}");

        setContentStart("[" + System.lineSeparator() + spacer(spaces));
        setFieldSeparator("," + System.lineSeparator() + spacer(spaces));
        setContentEnd(System.lineSeparator() + spacer(spaces - INDENT) + "]");
    }

    /**
     * Creates a StringBuilder responsible for the indenting.
     *
     * @param spaces how far to indent
     * @return a StringBuilder with {spaces} leading space characters.
     */
    private StringBuilder spacer(final int spaces) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < spaces; i++) {
            sb.append(" ");
        }
        return sb;
    }

    @Override
    public void appendDetail(final StringBuffer buffer, final String fieldName, final Object value) {
        if (!ClassUtils.isPrimitiveWrapper(value.getClass()) && !String.class.equals(value.getClass())
                && accept(value.getClass())) {
            spaces += INDENT;
            resetIndent();
            buffer.append(ReflectionToStringBuilder.toString(value, this, false, false,  true, null));
            spaces -= INDENT;
            resetIndent();
        } else {
            super.appendDetail(buffer, fieldName, value);
        }
    }

}
