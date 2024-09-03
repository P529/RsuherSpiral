package me.wasteofti.Commands;

import me.wasteofti.Modules.BasefinderModule;
import org.rusherhack.client.api.RusherHackAPI;
import org.rusherhack.client.api.feature.command.Command;
import org.rusherhack.core.command.annotations.CommandExecutor;

public class BasefinderSpiral extends Command {

    public BasefinderSpiral() {
        super("basefinderstart", "Set the start position of basefinder.");
    }

    @CommandExecutor
    private String basefinder() {
        //when return type is String you return the message you want to return to the user
        return "Set the start position using \"basefinder <x>,<z>\" or reset using \"basefinder reset\"" ;
    }

    /**
     * arguments example
     */
    @CommandExecutor
    @CommandExecutor.Argument({"String"}) //must set argument names
    private void basefinderWithArguments(String startPos) {
        if (startPos.equals("reset")) {
            BasefinderModule.setStartPos(0,0);
        }
        double startXDouble = Double.parseDouble(startPos.split(",")[0]);
        double startZDouble = Double.parseDouble(startPos.split(",")[1]);
        BasefinderModule.setStartPos(startXDouble, startZDouble);
        RusherHackAPI.getNotificationManager().info(String.format("Start position of Basefinder set to %s, %s", startPos.split(",")[0], startPos.split(",")[1]));
    }
}
