package com.graywolf336.jail;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.graywolf336.jail.beans.Cell;
import com.graywolf336.jail.beans.Jail;
import com.graywolf336.jail.beans.Prisoner;
import com.graywolf336.jail.enums.Lang;
import com.graywolf336.jail.enums.Settings;
import com.graywolf336.jail.events.PrePrisonerReleasedEvent;
import com.graywolf336.jail.events.PrisonerJailedEvent;
import com.graywolf336.jail.events.PrisonerReleasedEvent;
import com.graywolf336.jail.events.PrisonerTransferredEvent;

/**
 * Provides methods, non-statically, that do the preparing of jails, jailing, etc.
 * 
 * <p />
 * 
 * <ul>
 * 	<li>{@link #prepareJail(Jail, Cell, Player, Prisoner) preparejail}</li>
 * 	<li>{@link #jailPrisoner(Jail, Cell, Player, Prisoner) jailPrisoner}</li>
 * 	<li>{@link #schedulePrisonerRelease(Prisoner) schedulePrisonerRelease}</li>
 * 	<li>{@link #unJail(Jail, Cell, Player, Prisoner, CommandSender) unJail}</li>
 * 	<li>{@link #forceRelease(Prisoner, CommandSender) forceRelease}</li>
 *  <li>{@link #forceUnJail(Jail, Cell, Player, Prisoner, CommandSender) forceUnJail}</li>
 *  <li>{@link #transferPrisoner(Jail, Cell, Jail, Cell, Prisoner) transferPrisoner}</li>
 * </ul>
 * 
 * @author graywolf336
 * @since 2.x.x
 * @version 3.0.0
 */
public class PrisonerManager {
    private JailMain pl;
    private ArrayList<Prisoner> releases;

    public PrisonerManager(JailMain plugin) {
        this.pl = plugin;
        this.releases = new ArrayList<Prisoner>();

        // Schedule the releasing of prisoners
        plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, new Runnable() {
            public void run() {
                releaseScheduledPrisoners();
            }
        }, 100L, 20L);
    }

    /**
     * Does everything preparing for the jailing of the provided prisoner, if they are online it forwards it to {@link #jailPrisoner(Jail, Cell, Player, Prisoner)}.
     * 
     * <p />
     * 
     * In this we do the following:
     * <ol>
     * 	<li>Checks if the jail is null, if so it throws an Exception</li>
     * 	<li>Checks if the prisoner is null, if so it throws an Exception</li>
     * 	<li>Sets the prisoner data to offline pending or not, player == null</li>
     * 	<li>If the cell is null, add the prisoner data to the jail otherwise we set the cell's prisoner to this one. <em>Check before here if the cell already contains a prisoner.</em></li>
     * 	<li>Saves the jail information, goes out to the {@link JailIO} to initate a save.</li>
     * 	<li>If the prisoner is <em>not</em> offline, we will actually {@link #jailPrisoner(Jail, Cell, Player, Prisoner) jail} them now.</li>
     * 	<li>Does checks to get the right message for the next two items.</li>
     * 	<li>If we broadcast the jailing, then let's broadcast it.</li>
     * 	<li>If we log the jailing to console <em>and</em> we haven't broadcasted it, then we log it to the console.</li>
     * </ol>
     * 
     * @param jail The {@link Jail jail instance} we are sending this prisoner to
     * @param cell The name of the {@link Cell cell} we are sending this prisoner to
     * @param player The {@link Player player} we are preparing the jail for.
     * @param prisoner The {@link Prisoner prisoner} file.
     * @throws Exception if the jail or prisoner are null.
     */
    public void prepareJail(Jail jail, Cell cell, Player player, Prisoner prisoner) throws Exception {
        //Do some checks of whether the passed params are null.
        if(jail == null)
            throw new Exception("The jail can not be null.");

        if(prisoner == null)
            throw new Exception("Prisoner data can not be null.");

        //Set whether the prisoner is offline or not.
        prisoner.setOfflinePending(player == null);

        //Now that we've got those checks out of the way, let's start preparing.
        if(cell == null) {
            jail.addPrisoner(prisoner);
        }else {
            cell.setPrisoner(prisoner);
        }

        //If they are NOT offline, jail them
        if(!prisoner.isOfflinePending()) {
            jailPrisoner(jail, cell, player, prisoner);
        }

        //Get a message ready for broadcasting or logging.
        String msg = "";

        if(prisoner.getRemainingTime() < 0L)
            msg = Lang.BROADCASTMESSAGEFOREVER.get(prisoner.getLastKnownName());
        else
            msg = Lang.BROADCASTMESSAGEFORMINUTES.get(new String[] { prisoner.getLastKnownName(), String.valueOf(prisoner.getRemainingTimeInMinutes()) });

        boolean broadcasted = false;
        //Broadcast the message, if it is enabled
        if(pl.getConfig().getBoolean(Settings.BROADCASTJAILING.getPath(), false)) {
            pl.getServer().broadcastMessage(msg);
            broadcasted = true;
        }

        //Log the message, if it is enabled
        if(pl.getConfig().getBoolean(Settings.LOGJAILINGTOCONSOLE.getPath(), true) && !broadcasted) {
            pl.getServer().getConsoleSender().sendMessage(msg);
        }
    }

    /**
     * Jails the given player, <strong>only</strong> use when that player has offline data pending.
     * 
     * @param uuid of the player to jail.
     */
    public void jailPlayer(UUID uuid) {
        Jail j = pl.getJailManager().getJailPlayerIsIn(uuid);
        jailPrisoner(j, j.getCellPrisonerIsIn(uuid), pl.getServer().getPlayer(uuid), j.getPrisoner(uuid));
    }

    /**
     * Jails the prisoner with the proper information given.
     * 
     * @param jail where they are going
     * @param cell where they are being placed in, can be null
     * @param player who is the prisoner
     * @param prisoner data containing everything pertaining to them
     */
    public void jailPrisoner(Jail jail, Cell cell, Player player, Prisoner prisoner) {
        //If they have handcuffs on them, then let's remove them before we continue
        //this way the handcuff listeners and this aren't battleing each other
        if(pl.getHandCuffManager().isHandCuffed(player.getUniqueId())) {
            pl.getHandCuffManager().removeHandCuffs(player.getUniqueId());
        }

        //They are no longer offline, so set that.
        prisoner.setOfflinePending(false);

        //We are getting ready to teleport them, so set it to true so that
        //the *future* move checkers won't be canceling our moving.
        prisoner.setTeleporting(true);

        //If their reason is empty send proper message, else send other proper message
        if(prisoner.getReason().isEmpty()) {
            player.sendMessage(Lang.JAILED.get());
        }else {
            player.sendMessage(Lang.JAILEDWITHREASON.get(prisoner.getReason()));
        }

        //If the config has releasing them back to their previous position,
        //then let's set it in the prisoner data.
        if(pl.getConfig().getBoolean(Settings.RELEASETOPREVIOUSPOSITION.getPath(), false)) {
            prisoner.setPreviousPosition(player.getLocation());
        }

        //If the config has restoring their previous gamemode enabled,
        //then let's set it in their prisoner data.
        if(pl.getConfig().getBoolean(Settings.RESTOREPREVIOUSGAMEMODE.getPath(), false)) {
            prisoner.setPreviousGameMode(player.getGameMode());
        }

        //Set their gamemode to the one in the config, if we get a null value
        //from the parsing then we set theirs to adventure
        try {
            player.setGameMode(GameMode.valueOf(pl.getConfig().getString(Settings.JAILEDGAMEMODE.getPath(), "ADVENTURE").toUpperCase()));
        }catch(Exception e) {
            pl.getLogger().severe("Your jailedgamemode setting is incorrect, please fix.");
            player.setGameMode(GameMode.ADVENTURE);
        }

        //only eject them if they're inside a vehicle and also eject anyone else on top of them
        if(player.isInsideVehicle()) {
            player.getVehicle().eject();
            player.getPassenger().eject();
            player.eject();
        }

        //If we are ignoring the sleeping state of prisoners,
        //then let's set that
        if(pl.getConfig().getBoolean(Settings.IGNORESLEEPINGSTATE.getPath(), true)) {
            player.setSleepingIgnored(true);
        }

        //Get the max and min food level in the config
        int maxFood = pl.getConfig().getInt(Settings.FOODCONTROLMAX.getPath(), 20);
        int minFood = pl.getConfig().getInt(Settings.FOODCONTROLMIN.getPath(), 10);

        //If their food level is less than the min food level, set it to the min
        //but if it is higher than the max, set it to the max
        if (player.getFoodLevel() <  minFood) {
            player.setFoodLevel(minFood);
        } else if (player.getFoodLevel() > maxFood) {
            player.setFoodLevel(maxFood);
        }

        //If the cell doesn't equal null, then let's put them in the jail
        if(cell != null) {
            //check if we store the inventory
            if(pl.getConfig().getBoolean(Settings.JAILEDSTOREINVENTORY.getPath(), true)) {
                List<String> blacklist = pl.getConfig().getStringList(Settings.JAILEDINVENTORYBLACKLIST.getPath());
                //Check if there is a chest to store our items to and if it is a double chest, if not we will then serialize it
                if(cell.hasChest()) {
                    //Get the chest's inventory and then clear it
                    Inventory chest = cell.getChest().getInventory();
                    chest.clear();

                    //Get the separate inventory, so we can iterate of them
                    ItemStack[] inventory = player.getInventory().getContents();
                    ItemStack[] armor = player.getInventory().getArmorContents();

                    for(ItemStack item : inventory) {
                        if(item != null) {
                            if(!Util.isStringInsideList(blacklist, item.getType().toString())) {
                                int i = chest.firstEmpty();
                                if(i != -1) {//Check that we have got a free spot, should never happen but just in case
                                    chest.setItem(i, item);
                                }
                            }
                        }
                    }

                    for(ItemStack item : armor) {
                        if(item != null) {
                            if(!Util.isStringInsideList(blacklist, item.getType().toString())) {
                                int i = chest.firstEmpty();
                                if(i != -1) {//Check that we have got a free spot, should never happen but just in case
                                    chest.setItem(i, item);
                                }
                            }
                        }
                    }

                    player.getInventory().setArmorContents(null);
                    player.getInventory().clear();
                }else {
                    for(ItemStack item : player.getInventory().getContents())
                        if(item != null)
                            if(Util.isStringInsideList(blacklist, item.getType().toString()))
                                player.getInventory().remove(item);

                    for(ItemStack item : player.getInventory().getArmorContents())
                        if(item != null)
                            if(Util.isStringInsideList(blacklist, item.getType().toString()))
                                player.getInventory().remove(item);

                    String[] inv = Util.playerInventoryToBase64(player.getInventory());
                    prisoner.setInventory(inv[0]);
                    prisoner.setArmor(inv[1]);

                    player.getInventory().setArmorContents(null);
                    player.getInventory().clear();
                }
            }

            //Teleport them to the cell's teleport location
            //they will now be placed in jail.
            pl.debug("Teleporting " + player.getName() + " to " + jail.getName() + " in the cell " + cell.getName() + "'s in: " + jail.getTeleportIn().toString());
            player.teleport(cell.getTeleport());
        }else {
            //There is no cell we're jailing them to, so stick them in the jail
            if(pl.getConfig().getBoolean(Settings.JAILEDSTOREINVENTORY.getPath(), true)) {
                List<String> blacklist = pl.getConfig().getStringList(Settings.JAILEDINVENTORYBLACKLIST.getPath());

                for(ItemStack item : player.getInventory().getContents())
                    if(item != null)
                        if(Util.isStringInsideList(blacklist, item.getType().toString()))
                            player.getInventory().remove(item);

                for(ItemStack item : player.getInventory().getArmorContents())
                    if(item != null)
                        if(Util.isStringInsideList(blacklist, item.getType().toString()))
                            player.getInventory().remove(item);

                String[] inv = Util.playerInventoryToBase64(player.getInventory());
                prisoner.setInventory(inv[0]);
                prisoner.setArmor(inv[1]);

                player.getInventory().setArmorContents(null);
                player.getInventory().clear();
            }

            //Teleport them to the jail's teleport in location
            //They will now be placed in jail.
            pl.debug("Teleporting " + player.getName() + " to " + jail.getName() + "'s in: " + jail.getTeleportIn().toString());
            player.teleport(jail.getTeleportIn());
        }

        //Set them to not allowing teleporting, as we are not going to be moving them anymore
        //this way the move checkers will start checking this player.
        prisoner.setTeleporting(false);

        //Get the commands to execute after they are jailed
        //replace all of the %p% so that the commands can have a player name in them
        for(String command : pl.getConfig().getStringList(Settings.COMMANDSONJAIL.getPath())) {
            command = command.replaceAll("%p%", player.getName());
            pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), command);
        }

        //Add the scoreboard to them if it is enabled
        if(pl.getConfig().getBoolean(Settings.SCOREBOARDENABLED.getPath())) {
            pl.getScoreBoardManager().addScoreBoard(player, prisoner);
        }

        //Save the data, as we have changed it
        pl.getJailIO().saveJail(jail);

        //Call our custom event for when a prisoner is actually jailed.
        PrisonerJailedEvent event = new PrisonerJailedEvent(jail, cell, prisoner, player);
        pl.getServer().getPluginManager().callEvent(event);
    }

    /**
     * Schedules a prisoner to be released, this method is to be used <strong>async</strong>.
     * 
     * @param prisoner to be released.
     * @see {@link #unJail(Jail, Cell, Player, Prisoner, CommandSender)} - If you're wanting to unjail a prisoner.
     */
    public void schedulePrisonerRelease(Prisoner prisoner) {
        releases.add(prisoner);
    }

    private void releaseScheduledPrisoners() {
        ArrayList<Prisoner> lettingGo = new ArrayList<Prisoner>(releases);
        for(Prisoner p : lettingGo) {
            releases.remove(p);
            releasePrisoner(pl.getServer().getPlayer(p.getUUID()), p);
        }
    }

    /**
     * Release the given prisoner from jailing, does the checks if they are offline or not.
     * 
     * @param player we are releasing, can be null and if so they'll be treated as offline.
     * @param prisoner data to handle.
     */
    private void releasePrisoner(Player player, Prisoner prisoner) {
        if(player == null) {
            prisoner.setOfflinePending(true);
            prisoner.setRemainingTime(0);
        }else {
            Jail j = pl.getJailManager().getJailPlayerIsIn(player.getUniqueId());

            try {
                unJail(j, j.getCellPrisonerIsIn(player.getUniqueId()), player, prisoner, null);
            }catch(Exception e) {
                if(pl.inDebug()) {
                    e.printStackTrace();
                }

                pl.getLogger().severe("Unable to unjail the prisoner " + player.getName() + " because '" + e.getMessage() + "'.");
            }

        }
    }

    /**
     * Unjails a prisoner from jail, removing all their data.
     * 
     * <p />
     * 
     * Throws an exception if either the jail is null or the prisoner is null.
     * 
     * @param jail where the prisoner is located at
     * @param cell which the prisoner is in, can be null
     * @param player instance for the prisoner we're unjailing
     * @param prisoner data where everything resides
     * @param sender The {@link CommandSender} who unjailed this player, can be null.
     * @throws Exception
     */
    public void unJail(Jail jail, Cell cell, Player player, Prisoner prisoner, CommandSender sender) throws Exception {
        //Do some checks of whether the passed params are null.
        if(jail == null)
            throw new Exception("The jail can not be null.");

        if(prisoner == null)
            throw new Exception("Prisoner data can not be null.");

        //Throw the custom event which is called before we start releasing them
        PrePrisonerReleasedEvent preEvent = new PrePrisonerReleasedEvent(jail, cell, prisoner, player);
        pl.getServer().getPluginManager().callEvent(preEvent);

        //We are getting ready to teleport them, so set it to true so that
        //the *future* move checkers won't be canceling our moving.
        prisoner.setTeleporting(true);

        //In case they have somehow got on a vehicle, let's unmount
        //them so we can possibly teleport them
        if(player.isInsideVehicle()) {
            player.getVehicle().eject();
            player.getPassenger().eject();
            player.eject();
        }

        //In case we had set their sleeping state to be ignored
        //let's enable their sleeping state taking place again
        player.setSleepingIgnored(false);

        //If the config has us teleporting them back to their
        //previous position then let's do that
        boolean tpd = false;
        if(pl.getConfig().getBoolean(Settings.RELEASETOPREVIOUSPOSITION.getPath(), false)) {
            if(prisoner.getPreviousLocation() != null)
                tpd = player.teleport(prisoner.getPreviousLocation());
        }

        //If they haven't already been teleported and the config has us to teleport on release,
        //then we teleport players to the jail's free spot
        if(!tpd && pl.getConfig().getBoolean(Settings.TELEPORTONRELEASE.getPath(), true)) {
            player.teleport(jail.getTeleportFree());
        }

        //If we are to restore their previous gamemode and we have it stored,
        //then by all means let's restore it
        if(pl.getConfig().getBoolean(Settings.RESTOREPREVIOUSGAMEMODE.getPath(), false)) {
            player.setGameMode(prisoner.getPreviousGameMode());
        }

        //Now, let's restore their inventory
        //First up, clear their inventory
        player.closeInventory();
        player.getInventory().setArmorContents(null);
        player.getInventory().clear();

        //if the cell isn't null, let's check if the cell has a chest and if so then try out best to restore
        //the prisoner's inventory from that
        if(cell != null) {
            if(cell.hasChest()) {
                Inventory chest = cell.getChest().getInventory();

                for (ItemStack item : chest.getContents()) {
                    if (item == null || item.getType() == Material.AIR) continue;

                    if(item.getType().toString().toLowerCase().contains("helmet") && (player.getInventory().getHelmet() == null || player.getInventory().getHelmet().getType() == Material.AIR)) {
                        player.getInventory().setHelmet(item);
                    } else if(item.getType().toString().toLowerCase().contains("chestplate") && (player.getInventory().getChestplate() == null || player.getInventory().getChestplate().getType() == Material.AIR)) {
                        player.getInventory().setChestplate(item);
                    } else if(item.getType().toString().toLowerCase().contains("leg") && (player.getInventory().getLeggings() == null || player.getInventory().getLeggings().getType() == Material.AIR)) {
                        player.getInventory().setLeggings(item);
                    } else if(item.getType().toString().toLowerCase().contains("boots") && (player.getInventory().getBoots() == null || player.getInventory().getBoots().getType() == Material.AIR)) {
                        player.getInventory().setBoots(item);
                    } else if (player.getInventory().firstEmpty() == -1) {
                        player.getWorld().dropItem(player.getLocation(), item);
                    } else {
                        player.getInventory().addItem(item);
                    }
                }

                chest.clear();
            }else {
                Util.restoreInventory(player, prisoner);
            }

            pl.getJailIO().removePrisoner(jail, cell, prisoner);
            cell.removePrisoner();
        }else {
            Util.restoreInventory(player, prisoner);

            pl.getJailIO().removePrisoner(jail, prisoner);
            jail.removePrisoner(prisoner);
        }

        //Get the commands to execute prisoners are unjailed
        //replace all of the %p% so that the commands can have a player name in them
        for(String command : pl.getConfig().getStringList(Settings.COMMANDSONRELEASE.getPath())) {
            command = command.replaceAll("%p%", player.getName());
            pl.getServer().dispatchCommand(pl.getServer().getConsoleSender(), command);
        }

        //Remove the scoreboard to them if it is enabled
        if(pl.getConfig().getBoolean(Settings.SCOREBOARDENABLED.getPath())) {
            pl.getScoreBoardManager().removeScoreBoard(player);
        }

        //Call the prisoner released event as we have released them.
        PrisonerReleasedEvent event = new PrisonerReleasedEvent(jail, cell, prisoner, player);
        pl.getServer().getPluginManager().callEvent(event);

        player.sendMessage(Lang.UNJAILED.get());
        if(sender != null) sender.sendMessage(Lang.UNJAILSUCCESS.get(player.getName()));
    }

    /**
     * Forcefully unjails a {@link Prisoner prisoner} from {@link Jail}.
     * 
     * <p />
     * 
     * This method forcefully removes all the references to this prisoner,
     * meaning if they're offline the following won't happened:
     * <ul>
     * 	<li>Inventory restored</li>
     * 	<li>Teleported anywhere</li>
     *  <li>No messages sent, they'll be clueless.</li>
     * </ul>
     * 
     * But if they're online, it goes through the regular unjailing methods.
     * 
     * <p />
     * 
     * @param prisoner to release
     * @param sender who is releasing the prisoner, <em>can be null</em>
     */
    public void forceRelease(Prisoner prisoner, CommandSender sender) {
        Jail j = pl.getJailManager().getJailPrisonerIsIn(prisoner);
        forceUnJail(j, j.getCellPrisonerIsIn(prisoner.getUUID()), pl.getServer().getPlayer(prisoner.getUUID()), prisoner, sender);
    }

    /**
     * Forcefully unjails a {@link Prisoner prisoner} from {@link Jail}.
     * 
     * 
     * <p />
     * 
     * This method forcefully removes all the references to this prisoner,
     * meaning if they're offline the following won't happened:
     * <ul>
     * 	<li>Inventory restored</li>
     * 	<li>Teleported anywhere</li>
     *  <li>No messages sent, they'll be clueless.</li>
     * </ul>
     * 
     * But if they're online, it goes through the regular unjailing methods.
     * 
     * <p />
     * 
     * @param jail the prisoner is in
     * @param cell the prisoner is in, <em>can be null</em>
     * @param player of the prisoner, if this is null then the player won't be teleported when they come back on.
     * @param prisoner to release and remove data
     * @param sender who is releasing the prisoner, <em>can be null</em>
     */
    public void forceUnJail(Jail jail, Cell cell, Player player, Prisoner prisoner, CommandSender sender) {
        if(player == null) {
            //Player is offline, we just forcefully remove them from the database
            pl.getJailIO().removePrisoner(jail, cell, prisoner);

            if(cell == null) {
                jail.removePrisoner(prisoner);
            }else {
                cell.removePrisoner();
            }

            if(sender != null) sender.sendMessage(Lang.FORCEUNJAILED.get(prisoner.getLastKnownName()));
        }else {
            try {
                unJail(jail, cell, player, prisoner, sender);
            } catch (Exception e) {
                releasePrisoner(player, prisoner);
            }
        }
    }

    /**
     * Transfers the prisoner from one jail, or cell, to another jail, and/or cell.
     * 
     * @param originJail The jail where they are coming from.
     * @param originCell The cell where they are coming from.
     * @param targetJail The jail we're transferring them from.
     * @param targetCell The cell we're putting them into.
     * @param prisoner The prisoner data we're handling.
     */
    public void transferPrisoner(Jail originJail, Cell originCell, Jail targetJail, Cell targetCell, Prisoner prisoner) {
        Player player = pl.getServer().getPlayer(prisoner.getUUID());

        //If there is no origin cell, then we need to basically just put them to their targetJail
        if(originCell == null) {
            //But first thing is first, let's check if there is a targetCell we're putting them in
            if(targetCell == null) {
                //There is no cell, so we're just going to be putting them into
                //the target jail and that's it
                targetJail.addPrisoner(prisoner);
                //Now then let's remove them from their old jail
                originJail.removePrisoner(prisoner);

                //If the player is not online, trigger them to be teleported when they
                //come online again
                if(player == null) {
                    //Set them to have an action on offline pending, so it gets triggered
                    prisoner.setOfflinePending(true);
                    //Now let's set them to be transferred when they come online next
                    prisoner.setToBeTransferred(true);
                }else {
                    prisoner.setTeleporting(true);
                    player.teleport(targetJail.getTeleportIn());
                    prisoner.setTeleporting(false);
                    player.sendMessage(Lang.TRANSFERRED.get(targetJail.getName()));
                }
            }else {
                //They are set to go to the targetCell, so handle accordingly
                targetCell.setPrisoner(prisoner);

                //If the player is not online, trigger them to be teleported when they
                //come online again
                if(player == null) {
                    //Set them to have an action on offline pending, so it gets triggered
                    prisoner.setOfflinePending(true);
                    //Now let's set them to be transferred when they come online next
                    prisoner.setToBeTransferred(true);
                }else {
                    prisoner.setTeleporting(true);
                    player.teleport(targetCell.getTeleport());
                    prisoner.setTeleporting(false);
                    player.sendMessage(Lang.TRANSFERRED.get(targetJail.getName()));
                }
            }
        }else {
            //They are being transferred from a cell, so we need to handle getting the inventory
            //and all that sort of stuff from the old cell before we transfer them over to the new cell

            //If they're not being sent to a cell any more, handle that differently as well
            if(targetCell == null) {
                //Add them to the target jail
                targetJail.addPrisoner(prisoner);
                //Next, remove them from the cell
                originCell.removePrisoner();

                //If the cell they came from has any items from their inventory,
                //let's get it all and store it
                if(originCell.hasChest()) {
                    //Convert the inventory to base64 string and store it in the prisoner's file
                    prisoner.setInventory(Util.toBase64(originCell.getChest().getInventory()));
                    //Clear the origin cell's inventory so nothing is left behind
                    originCell.getChest().getInventory().clear();
                }
            }else {
                //They are being transferred to a cell in another cell,
                //we aren't going to do any sanity checks as we hope the method that is
                //calling this one does those sanity checks for us.

                //Set the cell's prisoner to this one
                targetCell.setPrisoner(prisoner);
                //Remove the prisoner from the old one
                originCell.removePrisoner();

                //Check if the origin cell has a chest, put all the player's inventory into it
                if(originCell.hasChest()) {
                    //If the targetCell has a chest
                    if(targetCell.hasChest()) {
                        //Loop through the origin's chest inventory and add it to the target cell's chest
                        for(ItemStack i : originCell.getChest().getInventory().getContents()) {
                            targetCell.getChest().getInventory().addItem(i);
                        }

                        //Clear the origin cell's chest as it is clear now
                        originCell.getChest().getInventory().clear();
                    }else {
                        //targetCell has no chest so we aren't going to try and put anything into it

                        //Convert the inventory to base64 string and store it in the prisoner's file
                        prisoner.setInventory(Util.toBase64(originCell.getChest().getInventory()));
                        //Clear the origin cell's inventory so nothing is left behind
                        originCell.getChest().getInventory().clear();
                    }
                }
            }
        }

        //Throw our custom event PrisonerTransferredEvent to say it was successful
        PrisonerTransferredEvent event = new PrisonerTransferredEvent(originJail, originCell, targetJail, targetCell, prisoner, player);
        pl.getServer().getPluginManager().callEvent(event);
    }
}
