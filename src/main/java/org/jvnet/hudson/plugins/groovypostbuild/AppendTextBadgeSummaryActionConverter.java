/*
 * The MIT License
 *
 * Copyright (c) 2026 Groovy Postbuild Plugin Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.jvnet.hudson.plugins.groovypostbuild;

import com.jenkinsci.plugins.badge.action.BadgeSummaryAction;
import com.thoughtworks.xstream.converters.MarshallingContext;
import com.thoughtworks.xstream.converters.reflection.ReflectionProvider;
import com.thoughtworks.xstream.io.HierarchicalStreamWriter;
import com.thoughtworks.xstream.mapper.Mapper;
import hudson.util.RobustReflectionConverter;

/**
 * Makes {@link AppendTextBadgeSummaryAction} persist as a plain {@link BadgeSummaryAction} in
 * {@code build.xml}, without touching Java's own serialization contract.
 *
 * <p>A {@code writeReplace()} method would do the same substitution, but it is honored by
 * <em>any</em> Java serialization, not just {@code Run.XSTREAM2}: Pipeline's CPS interpreter
 * persists a running program's local variables (including whatever a script assigned
 * {@code manager.createSummary(...)} to) with plain {@code ObjectOutputStream}/
 * {@code ObjectInputStream} across every durability checkpoint. A {@code writeReplace()}-based
 * shim would come back from that round trip as a plain {@code BadgeSummaryAction} too, so a script
 * that called {@code appendText(...)} again after the controller resumed would fail with
 * {@code MissingMethodException} - breaking in Pipeline the exact thing being fixed in freestyle.
 *
 * <p>Registering this as an XStream {@link com.thoughtworks.xstream.converters.Converter} instead
 * keeps the substitution confined to {@code Run.XSTREAM2}: only {@link #marshal} is overridden, to
 * write a plain {@code BadgeSummaryAction}'s fields (with the same {@code resolves-to} attribute
 * {@code writeReplace()} would have produced) in place of this class's own; {@link #unmarshal} is
 * inherited unchanged from {@link RobustReflectionConverter}, so reading is unaffected either way.
 * {@code ObjectOutputStream} never consults registered XStream converters, so CPS state keeps the
 * real shim, with {@code appendText} intact, across every checkpoint.
 */
public class AppendTextBadgeSummaryActionConverter extends RobustReflectionConverter {

    public AppendTextBadgeSummaryActionConverter(Mapper mapper, ReflectionProvider reflectionProvider) {
        super(mapper, reflectionProvider);
    }

    @Override
    public boolean canConvert(Class type) {
        return type == AppendTextBadgeSummaryAction.class;
    }

    @Override
    public void marshal(Object original, HierarchicalStreamWriter writer, MarshallingContext context) {
        AppendTextBadgeSummaryAction action = (AppendTextBadgeSummaryAction) original;
        BadgeSummaryAction replacement = action.toPlainBadgeSummaryAction();
        writer.addAttribute(
                mapper.aliasForSystemAttribute("resolves-to"), mapper.serializedClass(replacement.getClass()));
        super.marshal(replacement, writer, context);
    }
}
