package com.tkisor.nekojs.bindings.event;

import com.mojang.brigadier.ParseResults;
import com.tkisor.nekojs.bindings.server.ServerNekoEvent;
import lombok.Getter;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.neoforge.event.CommandEvent;

public class CommandNekoEvent extends ServerNekoEvent {
    private final CommandEvent event;
    @Getter
    private final String commandName;

    public CommandNekoEvent(CommandEvent event) {
        super(event.getParseResults().getContext().getSource().getServer());
        this.event = event;
        this.commandName = event.getParseResults().getContext().getNodes().isEmpty() ? "" : event.getParseResults().getContext().getNodes().getFirst().getNode().getName();
    }

    public String getInput() {
        return event.getParseResults().getReader().getString();
    }

    public ParseResults<CommandSourceStack> getParseResults() {
        return event.getParseResults();
    }

    public void setParseResults(ParseResults<CommandSourceStack> parse) {
        event.setParseResults(parse);
    }

    public Throwable getException() {
        return event.getException();
    }

    public void setException(Throwable exception) {
        event.setException(exception);
    }
}
