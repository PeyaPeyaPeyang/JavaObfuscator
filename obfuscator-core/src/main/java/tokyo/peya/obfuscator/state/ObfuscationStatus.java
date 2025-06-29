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
