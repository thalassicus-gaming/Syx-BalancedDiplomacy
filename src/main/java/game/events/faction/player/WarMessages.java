// WarMessages.java
// Document Version 1.0.0
// Creation date: 2026/07/25
// Creator: Thalassicus

package game.events.faction.player;

import game.faction.FACTIONS;
import game.faction.diplomacy.DIP;
import game.faction.diplomacy.deal.Deal;
import game.faction.diplomacy.deal.DealDrawfter;
import game.faction.npc.FactionNPC;
import game.faction.royalty.opinion.ROPINION;
import init.race.KingMessages;
import snake2d.util.sprite.text.Str;
import thal.diplomacy.ThalDiplomacy;
import util.text.D;
import view.ui.diplomacy.UIDipMess;
import view.ui.diplomacy.UIDipMessAction;
import view.ui.diplomacy.UIDipMessDeal;
import view.ui.message.MessageText;
import world.map.regions.Region;

class WarMessages {
   private static CharSequence ¤¤War = "War!";
   private static CharSequence ¤¤WarD = "The enemy has shown itself. Let us muster and fight!";
   private static CharSequence ¤¤Crusade = "More enemies";
   private static CharSequence ¤¤CrusadeD = "This faction joins our enemies.";
   private static CharSequence ¤¤CrusadeD2 = "This faction of {0} and {1} have now banded together against you. They call themselves: {1}.";
   private static CharSequence ¤¤WarByProxy = "Proxy war!";
   private static CharSequence ¤¤WarByProxyD = "My lord, even in distant places they manage to hate our freedom and way of life. The distant faction {0} has aided our mortal enemy, {1}, bolstering their armies!";
   private static CharSequence ¤¤rumour = "¤Bad Rumours";
   private static CharSequence ¤¤rumourD = "¤It has come to our attention that a rumour has been spread. That you {0}. Now, while those close to you know this isn't true, it has affected your standing amongst the other factions of Syx significantly. We do not know who started the rumours, but we suspect the ruler of {1} might have had something to do with it. On the bright side, we now have more international support should we choose to attack this faction.";
   private static CharSequence ¤¤terror = "¤Terrorists";
   private static CharSequence ¤¤terrorD = "¤In the region of {0} there has emerged an organization calling themselves {1}. These are violently opposing your rule and destabilizing the affiliation of the region. Apparently, they have received backing and funds from elsewhere, we suspect the ruler of {2}. On the bright side, we now have more international support should we choose to attack this faction.";
   static CharSequence ¤¤teachings = "¤Bad Teachings";
   private static CharSequence ¤¤teachingsD = "¤Someone has been spreading leaflets about the teachings of the Burnt Prophet, filling the heads of our subjects with ideas such as all are created equal, pacifism, self rule and other rubbish! As a result, our subjects loyalty will be diminished for some time. We do not know who is responsible to this, but the paper smells like it's from the faction on {0}. On the bright side, we now have more international support should we choose to attack this faction.";
   private static CharSequence ¤¤Demand = "Demand";
   private static CharSequence ¤¤DemandD = "Failure to comply with this request can have unforeseen consequences.";
   private static CharSequence ¤¤breakTitle = "¤Freedom request";
   private static CharSequence ¤¤breakBody = "¤This faction asks that you release them from their bounds. The faction will become your colleague, and the faction will be very grateful should you accept. Decline and they might get bad ideas.";

   static {
      D.ts(WarMessages.class);
   }

   static void start(FactionNPC f) {
      KingMessages.Message m = f.king().induvidual.race().kingMessage().WAR_NORMAL;
      if (DIP.VASSAL().is(f)) {
         m = f.king().induvidual.race().kingMessage().WAR_VASSAL;
      } else if (DIP.get(f).ally) {
         m = f.king().induvidual.race().kingMessage().WAR_ALLY;
      }

      new UIDipMess(¤¤War, m.get(f), ¤¤WarD, f).send();
   }

   static void join(FactionNPC f) {
      KingMessages.Message m = f.king().induvidual.race().kingMessage().WAR_JOIN_NORMAL;
      if (DIP.VASSAL().is(f)) {
         m = f.king().induvidual.race().kingMessage().WAR_JOIN_VASSAL;
      } else if (DIP.get(f).ally) {
         m = f.king().induvidual.race().kingMessage().WAR_JOIN_ALLY;
      }

      CharSequence dd = ¤¤CrusadeD;
      if (DIP.WAR().all(FACTIONS.player()).size() == 2) {
         Str.TMP
            .clear()
            .add(¤¤CrusadeD2)
            .insert(0, DIP.WAR().all(FACTIONS.player()).get(0).name)
            .insert(1, DIP.WAR().all(FACTIONS.player()).get(1).name)
            .insert(2, DIP.WAR_PLAYER().teamName);
         dd = Str.TMP;
      }

      new UIDipMess(¤¤Crusade, m.get(f), dd, f).send();
   }

   static void proxy(FactionNPC atWar, FactionNPC supplier, int am) {
      new UIDipMess(¤¤WarByProxy, Str.TMP.clear().add(¤¤WarByProxyD).insert(0, supplier.name).insert(1, atWar.name), null, atWar).send();
   }

   static void poision(FactionNPC source) {
      new MessageText(¤¤rumour).paragraph(Str.TMP.clear().add(¤¤rumourD).insert(0, source.race().kingMessage().RUMOUR.rnd()).insert(1, source.name)).send();
   }

   static void ngo(FactionNPC source, Region target) {
      new MessageText(¤¤terror)
         .paragraph(Str.TMP.clear().add(¤¤terrorD).insert(0, target.info.name()).insert(1, source.race().kingMessage().NGO.rnd()).insert(2, source.name))
         .send();
   }

   static void teachings(FactionNPC source) {
      new MessageText(¤¤teachings).paragraph(Str.TMP.clear().add(¤¤teachingsD).insert(0, source.name)).send();
   }

   static void warn(FactionNPC f) {
      if (DIP.VASSAL().is(f)) {
         new WarMessages.VassalRequest(f).send();
      } else {
         Deal d = DIP.TMP();
         d.setFactionAndClear(f);
         // START EDIT
         // Vanilla measured against selfWorth, which counts every non-capitol region,
         // and drafted with regions enabled, so a peacetime threat could take land.
         // Accepting is the only exit from the trust cycle that produces these
         // demands, so the price has to stay within reach for that exit to exist.
         double demandWorth = ThalDiplomacy.calculateExtortionWorth(d.player, f);
         DealDrawfter.draft(d, -demandWorth, true, false);
         // END EDIT
         if (d.hasDeal()) {
            KingMessages.Message m = f.king().induvidual.race().kingMessage().THREAT_NORMAL;
            if (DIP.get(f).transit) {
               m = f.king().induvidual.race().kingMessage().THREAT_ALLY;
            }

            double v = ROPINION.GIFTS().getGenerosityNeededForPeace(f);
            new UIDipMessDeal(¤¤Demand, m.get(f), ¤¤DemandD, d, v, -0.5).send();
         }
      }
   }

   private static class VassalRequest extends UIDipMessAction {
      private static final long serialVersionUID = 1L;

      public VassalRequest(FactionNPC f) {
         super(WarMessages.¤¤breakTitle, f.king().induvidual.race().kingMessage().THREAT_VASSAL.get(f), WarMessages.¤¤breakBody, f, f, 1.0, -1.0);
      }

      @Override
      protected void accept(FactionNPC f, FactionNPC o) {
         DIP.PACT().set(f);
         ROPINION.OTHER().liberate(f);
      }

      @Override
      protected boolean valid(FactionNPC f, FactionNPC o) {
         return DIP.VASSAL().is(f);
      }
   }
}
