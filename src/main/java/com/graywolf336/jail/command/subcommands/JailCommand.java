package com.graywolf336.jail.command.subcommands;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import com.graywolf336.jail.JailManager;
import com.graywolf336.jail.Util;
import com.graywolf336.jail.beans.Cell;
import com.graywolf336.jail.beans.Jail;
import com.graywolf336.jail.beans.Prisoner;
import com.graywolf336.jail.command.Command;
import com.graywolf336.jail.command.CommandInfo;
import com.graywolf336.jail.command.commands.jewels.Jailing;
import com.graywolf336.jail.enums.Lang;
import com.graywolf336.jail.enums.Settings;
import com.graywolf336.jail.events.PrePrisonerJailedEvent;
import com.graywolf336.jail.interfaces.ICell;
import com.lexicalscope.jewel.cli.ArgumentValidationException;
import com.lexicalscope.jewel.cli.CliFactory;

@CommandInfo(
        maxArgs = -1,
        minimumArgs = 0,
        needsPlayer = false,
        pattern = "jail|j",
        permission = "jail.command.jail",
        usage = "/jail [name] (-t time) (-j JailName) (-c CellName) (-a AnyCell) (-m Muted) (-r A reason for jailing)"
        )
public class JailCommand implements Command {
    private static final String noJailPermission = "jail.cantbejailed";
    private List<String> commands = Arrays.asList(new String[] { "p", "t", "i", "j", "c", "a", "m", "r", "f" });

    /*
     * Executes the command. Checks the following:
     * 
     * - If there are any jails.
     * - If the command can be parsed correctly.
     * - If the player is already jailed.
     * - If the given time can be parsed correctly, defaults to what is defined in the config
     * - If the jail is reasonable or not, else sets the one from the config
     * - If the cell is not empty then checks to be sure that cell exists
     * - If the prisoner is online or not.
     */
    @SuppressWarnings("deprecation")
    public boolean execute(JailManager jm, CommandSender sender, String... args) {
        if(jm.getJails().isEmpty()) {
            sender.sendMessage(Lang.NOJAILS.get());
            return true;
        }

        //This is just to add the -p param so CliFactory doesn't blow up
        List<String> arguments = new LinkedList<String>(Arrays.asList(args));
        //Only add the "-p" if it doesn't already contain it, this way people can do `/jail -p check` in the event someone
        //has a name which is one of our subcommands
        if(!arguments.contains("-p")) arguments.add(0, "-p");

        Jailing params = null;

        try {
            params = CliFactory.parseArguments(Jailing.class, arguments.toArray(new String[arguments.size()]));
        }catch(ArgumentValidationException e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
            return true;
        }

        //Check if they've actually given us a player to jail
        if(params.getPlayer() == null) {
            sender.sendMessage(Lang.PROVIDEAPLAYER.get(Lang.JAILING));
            return true;
        }else {
            jm.getPlugin().debug("We are getting ready to handle jailing: " + params.getPlayer());
        }

        //Check if the given player is already jailed or not
        if(jm.isPlayerJailedByLastKnownUsername(params.getPlayer())) {
            sender.sendMessage(Lang.ALREADYJAILED.get(params.getPlayer()));
            return true;
        }
        
        Player p = jm.getPlugin().getServer().getPlayer(params.getPlayer());
        
        //If the player instance is not null and the player has the permission
        //'jail.cantbejailed' then don't allow this to happen
        if(p != null && p.hasPermission(noJailPermission)) {
            sender.sendMessage(Lang.CANTBEJAILED.get());
            return true;
        }
        
        String uuid = "";
        if(p == null) {
            if (!jm.getPlugin().getConfig().getBoolean(Settings.ALLOWJAILINGOFFLINEPLAYERS.getPath())) {
                sender.sendMessage(Lang.PLAYERHASNEVERPLAYEDBEFORE.get());
                return true;
            }

            //TODO: Make this whole jail command non-blocking
            OfflinePlayer of = jm.getPlugin().getServer().getOfflinePlayer(params.getPlayer());
            
            if(!of.hasPlayedBefore() && !jm.getPlugin().getConfig().getBoolean(Settings.ALLOWJAILINGNEVERPLAYEDBEFOREPLAYERS.getPath()) && !params.isForce()) {
                sender.sendMessage(Lang.PLAYERHASNEVERPLAYEDBEFORE.get(params.getPlayer()));
                return true;
            }else {
                uuid = of.getUniqueId().toString();
            }
        }else {
            uuid = p.getUniqueId().toString();
        }

        //Try to parse the time, if they give us nothing in the time parameter then we get the default time
        //from the config and if that isn't there then we default to thirty minutes.
        Long time = 10L;
        try {
            if(params.isTime()) {
                time = Util.getTime(params.getTime());
            }else {
                time = Util.getTime(jm.getPlugin().getConfig().getString(Settings.DEFAULTTIME.getPath(), "30m"));
            }
        }catch(Exception e) {
            sender.sendMessage(Lang.NUMBERFORMATINCORRECT.get());
            return true;
        }
        
        //Check if they provided the infinite argument
        //if so, then set the time jailed forever
        if(params.isInfinite()) {
            time = -1L;
        }

        //Check the jail params. If it is empty, let's get the default jail
        //from the config. If that is nearest, let's make a call to getting the nearest jail to
        //the sender but otherwise if it isn't nearest then let's set it to the default jail
        //which is defined in the config.
        String jailName = "";
        if(!params.isJail()) {
            String dJail = jm.getPlugin().getConfig().getString(Settings.DEFAULTJAIL.getPath());

            if(dJail.equalsIgnoreCase("nearest")) {
                jailName = jm.getNearestJail(sender).getName();
            }else {
                jailName = dJail;
            }
        }else if(!jm.isValidJail(params.getJail())) {
            sender.sendMessage(Lang.NOJAIL.get(params.getJail()));
            return true;
        }else {
            jailName = params.getJail();
        }

        //Get the jail instance from the name of jail in the params.
        Jail j = jm.getJail(jailName);
        if(!j.isEnabled()) {
            sender.sendMessage(Lang.WORLDUNLOADED.get(j.getName()));
            return true;
        }

        ICell c = null;
        //Check if the cell is defined
        if(params.isCell()) {
            //Check if it is a valid cell
            if(!jm.getJail(jailName).isValidCell(params.getCell())) {
                //There is no cell by that name
                sender.sendMessage(Lang.NOCELL.get(new String[] { params.getCell(), jailName }));
                return true;
            }else if(jm.getJail(jailName).getCell(params.getCell()).hasPrisoner()) {
                //If the cell has a prisoner, don't allow jailing them to that particular cell but suggest another one
                sender.sendMessage(Lang.CELLNOTEMPTY.get(params.getCell()));
                Cell suggestedCell = jm.getJail(jailName).getFirstEmptyCell();
                if(suggestedCell != null) {
                    sender.sendMessage(Lang.SUGGESTEDCELL.get(new String[] { jailName, suggestedCell.getName() }));
                }else {
                    sender.sendMessage(Lang.NOEMPTYCELLS.get(jailName));
                }

                return true;
            }else {
                c = jm.getJail(jailName).getCell(params.getCell());
            }
        }

        //If they want just any open cell or automatic jailing in cells is turned on
        //and a cell wasn't already found, then find try to find a cell
        if(params.isAnyCell() && c == null) {
            c = jm.getJail(jailName).getFirstEmptyCell();
            if(c == null) {
                //If there wasn't an empty cell, then tell them so.
                sender.sendMessage(Lang.NOEMPTYCELLS.get(jailName));
                return true;
            }
        }else if(jm.getPlugin().getConfig().getBoolean(Settings.AUTOMATICCELL.getPath(), true) && j.hasCells() && c == null) {
            c = jm.getJail(jailName).getFirstEmptyCell();
            if(c == null) {
                //If there wasn't an empty cell, then tell them so.
                sender.sendMessage(Lang.NOEMPTYCELLS.get(jailName));
                return true;
            }
        }

        //If the jailer gave no reason, then let's get the default reason
        String reason = "";
        if(params.isReason()) {
            StringBuilder sb = new StringBuilder();
            for(String s : params.getReason()) {
                sb.append(s).append(' ');
            }

            sb.deleteCharAt(sb.length() - 1);
            reason = sb.toString();
            
        }else {
        	reason = Lang.DEFAULTJAILEDREASON.get();
        }

        //If the config has automatic muting, then let's set them as muted
        boolean muted = params.getMuted();
        if(jm.getPlugin().getConfig().getBoolean(Settings.AUTOMATICMUTE.getPath())) {
            muted = true;
        }

        Prisoner pris = new Prisoner(uuid, params.getPlayer(), muted, time, sender.getName(), reason);

        //call the event
        PrePrisonerJailedEvent event = new PrePrisonerJailedEvent(j, c, pris, p, p == null, pris.getJailer());
        jm.getPlugin().getServer().getPluginManager().callEvent(event);

        //check if the event is cancelled
        if(event.isCancelled()) {
            if(event.getCancelledMessage().isEmpty())
                sender.sendMessage(Lang.CANCELLEDBYANOTHERPLUGIN.get(params.getPlayer()));
            else
                sender.sendMessage(event.getCancelledMessage());

            return true;
        }

        //recall data from the event
        j = event.getJail();
        c = event.getCell();
        pris = event.getPrisoner();
        p = event.getPlayer();

        //Player is not online
        if(p == null) {
            sender.sendMessage(Lang.OFFLINEJAIL.get(new String[] { pris.getLastKnownName(), String.valueOf(pris.getRemainingTimeInMinutes()) }));
        }else {
            //Player *is* online
            sender.sendMessage(Lang.ONLINEJAIL.get(new String[] { pris.getLastKnownName(), String.valueOf(pris.getRemainingTimeInMinutes()) }));
        }

        try {
            jm.getPlugin().getPrisonerManager().prepareJail(j, c, p, pris);
        } catch (Exception e) {
            sender.sendMessage(ChatColor.RED + e.getMessage());
            return true;
        }

        return true;
    }

    public List<String> provideTabCompletions(JailManager jm, CommandSender sender, String... args) throws Exception {
        //by the time it gets to this command it'll have at least two arguments
        String last = args[args.length - 1];
        
        jm.getPlugin().debug("The last item is: " + last);
        
        if(last.isEmpty() || !commands.contains(last.replace("-", ""))) {
            //the current part is empty. Need to look at their previous
            //item and if it is a valid option, then provide them a valid tab complete option
            if(args.length - 2 > -1) {
                String previous = args[args.length - 2];
                jm.getPlugin().debug("args[args.length - 2]: " + previous);
                
                if(previous.equalsIgnoreCase("-p")) return getPlayers(jm, last);
                else if(previous.equalsIgnoreCase("-j")) return jm.getJailsByPrefix(last);
                else if(previous.equalsIgnoreCase("-c")) {
                    //Since we need to give them a list of the cells in a jail
                    //we need to get the jail they're giving
                    int jailIndex = ArrayUtils.indexOf(args, "-j");
                    if(jailIndex != -1) {
                        String jail = args[jailIndex + 1];
                        jm.getPlugin().debug("The jail is: " + jail);
                        if(jm.isValidJail(jail)) return getCells(jm, jail, last);
                    }
                }else if(previous.endsWith("r")) return Collections.emptyList();
                else if(!commands.contains(args[args.length - 2].replace("-", ""))) return Util.getUnusedItems(commands, args, false);
            }else {
            	return getPlayers(jm, last);
            }
        }else if(last.equalsIgnoreCase("-")) {
            //add some smart checking so that it only returns a list of what isn't already
            //in the command :)
            return Util.getUnusedItems(commands, args, false);
        }else {
        	jm.getPlugin().debug("Getting the list of online players.");
            return getPlayers(jm, last);
        }
        
        jm.getPlugin().debug("Returning an empty list.");
        return Collections.emptyList();
    }
    
    private List<String> getPlayers(JailManager jm, String first) {
        List<String> results = new ArrayList<String>();
        
        for(Player p : jm.getPlugin().getServer().getOnlinePlayers())
            if(first.isEmpty() || StringUtil.startsWithIgnoreCase(p.getName(), first))
                if(!jm.isPlayerJailed(p.getUniqueId()) && !p.hasPermission(noJailPermission)) //don't send back them if they're already jailed or can't be jailed
                    results.add(p.getName());
        
        Collections.sort(results);
        jm.getPlugin().debug("The list we're returning is: " + Util.getStringFromList(", ", results));
        
        return results;
    }
    
    private List<String> getCells(JailManager jm, String jail, String cell) {
        List<String> results = new ArrayList<String>();
        
        for(Cell c : jm.getJail(jail).getCells())
            if(!c.hasPrisoner() && (cell.isEmpty() || StringUtil.startsWithIgnoreCase(c.getName(), cell)))
            	results.add(c.getName());
        
        Collections.sort(results);
        
        return results;
    }
}
