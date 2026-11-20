package com.omniarena.commands.admin;

import com.omniarena.OmniArena;
import com.omniarena.commands.SubCommand;
import com.omniarena.commands.TabUtil;
import com.omniarena.config.ConfigManager;
import com.omniarena.health.Doctor;
import com.omniarena.health.Watchdog;
import com.omniarena.permission.PermissionChecker;
import com.omniarena.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

import java.util.List;

/** /oa doctor: health report; /oa doctor fix: run the watchdog fixes now. */
public class DoctorCommand extends SubCommand {

    private final OmniArena plugin;
    private final Doctor doctor;

    public DoctorCommand(OmniArena plugin) {
        super(PermissionChecker.ADMIN_DOCTOR, "/oa doctor [fix]", "Check OmniArena's health and fix problems");
        this.plugin = plugin;
        this.doctor = new Doctor(plugin);
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        ConfigManager cm = ConfigManager.getInstance();
        if (args.length > 0 && args[0].equalsIgnoreCase("fix")) {
            Watchdog wd = Watchdog.getInstance();
            if (wd == null || plugin.isStopped()) {
                MessageUtil.send(sender, cm.getMessage("doctor-stopped"));
                return;
            }
            List<String> fixes = wd.runChecks();
            if (fixes.isEmpty()) {
                MessageUtil.send(sender, cm.getMessage("doctor-fix-none"));
                return;
            }
            MessageUtil.send(sender, cm.getMessage("doctor-fix-done").replace("{count}", String.valueOf(fixes.size())));
            for (String f : fixes) {
                MessageUtil.send(sender, cm.getRawMessage("doctor-info").replace("{text}", MiniMessage.miniMessage().escapeTags(f)));
            }
            return;
        }
        if (args.length > 0) {
            MessageUtil.send(sender, cm.getMessage("invalid-args").replace("{usage}", getUsage()));
            return;
        }
        doctor.report(sender);
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 1) return TabUtil.filter(List.of("fix"), args[0]);
        return List.of();
    }
}
