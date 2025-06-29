/*
 * Copyright (c) 2017-2019 superblaubeere27, Sam Sun, MarcoMC
 * Copyright (c) 2025 Peyang
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package tokyo.peya.obfuscator.state;

import lombok.Getter;

import java.util.EnumMap;
import java.util.function.Consumer;

@Getter
public class ObfuscationStatus
{
    private final EnumMap<ObfuscationState, Consumer<StatusContext>> stateChangeListeners = new EnumMap<>(ObfuscationState.class);

    private ObfuscationState state = ObfuscationState.NONE;
    private StatusContext context;

    public void setState(ObfuscationState state, StatusContext context)
    {
        if (state == null)
            throw new IllegalArgumentException("State cannot be null");


        if (this.state != state)
        {
            this.state = state;
            this.context = context;
            this.onAnythingChange();
        }
    }

    public <T extends StatusContext> void setStateChangeListener(ObfuscationState state, Class<T> statusClass, Consumer<T> listener)
    {
        if (state == null || listener == null)
            throw new IllegalArgumentException("State and listener cannot be null");
        if (!(state.getContextClass() == null || state.getContextClass().isAssignableFrom(statusClass)))
            throw new IllegalArgumentException("Context does not match the expected type for state: " + state);

        // 型安全を保証できないのでキャスト（ここは suppress するしかない）
        @SuppressWarnings("unchecked")
        Consumer<StatusContext> castedListener = (Consumer<StatusContext>) listener;
        this.stateChangeListeners.put(state, castedListener);
    }

    public void onAnythingChange()
    {
        Consumer<StatusContext> listener = this.stateChangeListeners.get(this.state);
        if (listener != null)
            listener.accept(this.context);
    }
}
