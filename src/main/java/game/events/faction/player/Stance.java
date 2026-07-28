// Stance.java
// Document Version 1.0.2
// Creation date: 2026/07/25
// Creator: Thalassicus

package game.events.faction.player;

import game.boosting.BOOSTABLES;
import game.events.faction.player.EventDiplomacy;
import game.faction.FACTIONS;
import game.faction.diplomacy.DIP;
import game.faction.diplomacy.DipStance;
import game.faction.diplomacy.deal.Deal;
import game.faction.diplomacy.deal.DealBool;
import game.faction.diplomacy.deal.DealDrawfter;
import game.faction.npc.FactionNPC;
import game.faction.royalty.opinion.ROPINION;
import game.time.TIME;
import init.race.KingMessages;
import settlement.main.SETT;
import settlement.stats.Induvidual;
import snake2d.util.rnd.RND;
import snake2d.util.sprite.text.Str;
import thalassicus.diplomacy.ThalDiplomacy;
import util.text.D;
import view.ui.diplomacy.UIDipMess;
import view.ui.diplomacy.UIDipMessDeal;
import view.ui.message.MessageText;
import world.region.RD;

final class Stance {
   private static CharSequence ¤¤Welcome = "Welcome";
   private static CharSequence ¤¤AgreementCancelled = "¤Agreement Cancelled.";
   private static CharSequence ¤¤AgreementCancelledD = "¤This faction has gone from the stance of {0} to the stance of {1}.";
   private static CharSequence ¤¤Warning = "¤Relations Worsen.";
   private static CharSequence ¤¤WarningD = "¤This faction is currently your {0}. If their opinion is not raised in time, it is possible they'll cancel this agreement.";
   private static CharSequence ¤¤TradeCancelled = "¤Agreements Cancelled.";
   private static CharSequence ¤¤TradeCancelledD = "¤Since the faction of {0} is no longer reachable to us, all agreements have been annulled.";
   private static CharSequence ¤¤title = "Proposal: {0}";

   static {
      D.ts(Stance.class);
   }

   boolean process(FactionNPC fa, Induvidual king, EventDiplomacy.EData data) {
      if (DIP.secondSinceStance(fa) < TIME.secondsPerDay()) {
         return false;
      }

      if (!RD.DIST().reachable(fa)) {
         return false;
      }

      KingMessages m = king.race().kingMessage();
      if (DIP.get(fa).trades && !RD.DIST().reachable(fa)) {
         DIP.NEUTRAL().set(fa, FACTIONS.player());
         new MessageText(¤¤TradeCancelled, Str.TMP.clear().add(¤¤TradeCancelledD).insert(0, fa.name)).send();
         return true;
      }

      double opinion = ROPINION.get(fa);
      if (DIP.TRADE().is(fa)) {
         return opinion < DIP.TRADE().opinionNeeded * 0.75 ? messDown(fa, DIP.NEUTRAL(), DIP.TRADE(), data) : false;
      }

      if (DIP.PACT().is(fa)) {
         return opinion < DIP.PACT().opinionNeeded * 0.75 ? messDown(fa, DIP.TRADE(), DIP.PACT(), data) : false;
      }

      if (DIP.ALLY().is(fa) && opinion < DIP.ALLY().opinionNeeded * 0.75) {
         return messDown(fa, DIP.PACT(), DIP.ALLY(), data);
      }

      if (!SETT.ROOMS().IMPORT.reqs.passes(FACTIONS.player())) {
         return false;
      }

      if (data.welcomed || !DIP.NEUTRAL().is(fa)) {
         boolean chance = RND.oneIn(32 * (1 + RD.DIST().neighs().size()));
         if (!chance) {
            return false;
         } else if (DIP.NEUTRAL().is(fa) && opinion > DIP.TRADE().opinionNeeded + 0.5) {
            messUp(fa, DIP.TMP().bools.TRADE, DIP.TRADE());
            return true;
         } else if (DIP.TRADE().is(fa) && opinion > DIP.PACT().opinionNeeded + 0.5) {
            messUp(fa, DIP.TMP().bools.PACT, DIP.TRADE());
            return true;
         } else if (DIP.PACT().is(fa) && opinion > DIP.ALLY().opinionNeeded + 0.5) {
            messUp(fa, DIP.TMP().bools.ALLY, DIP.TRADE());
            return true;
         } else {
            return false;
         }
      } else {
         if (!RND.oneIn(4)) {
            return false;
         }

         if (ROPINION.get(fa) > 0.4) {
            Deal d = DIP.TMP();
            d.setFactionAndClear(fa);
            double max = this.giftWorth(fa);
            if (max > 0.0) {
               DealDrawfter.draft(d, max, false, false);
               if (d.hasDeal()) {
                  data.welcomed = true;
                  new UIDipMessDeal(¤¤Welcome, m.GREETING_GOOD.get(fa), d, 0.0, -0.1).send();
                  return true;
               }
            }
         }

         new UIDipMess(¤¤Welcome, m.GREETING_BAD.get(fa), "", fa).send();
         data.welcomed = true;
         return false;
      }
   }

   private static boolean messDown(FactionNPC fa, DipStance downTo, DipStance current, EventDiplomacy.EData data) {
      if (fa.request.has()) {
         return false;
      }

      KingMessages m = fa.court().king().roy().induvidual.race().kingMessage();
      if (data.stanceMess) {
         Str.TMP.clear().add(¤¤AgreementCancelledD);
         Str.TMP.insert(0, DIP.get(fa).name);
         Str.TMP.insert(1, downTo.name);
         new UIDipMess(¤¤AgreementCancelled, m.STANCE_DOWN.get(fa), Str.TMP, fa).send();
         downTo.set(fa);
         data.stanceMess = false;
      } else {
         Str.TMP.clear().add(¤¤WarningD);
         Str.TMP.insert(0, DIP.get(fa).name);
         // START EDIT
         double more = ROPINION.GIFTS().getGenerosityNeededForOpinion(fa, ThalDiplomacy.agreementTargetOpinion(current));
         // END EDIT
         Deal d = DIP.TMP();
         d.setFactionAndClear(fa);
         double am = d.getWorthOfOpinion(more) * 0.9;
         DealDrawfter.draft(d, -am, false, false);
         // START EDIT
         // Writing the shortfall straight into the credits field bypasses the clamp
         // against credits.max(), which then fails DealSave validation and greys out
         // the accept button. Ask only for what the drafter could actually assemble.
         double draftedWorth = d.valueCredits();
         double achievableOpinion = ThalDiplomacy.proportionalOpinion(more, am, draftedWorth);
         ThalDiplomacy.logAgreementWarning(fa, current, more, am, draftedWorth, achievableOpinion);
         data.stanceMess = true;
         new UIDipMessDeal(¤¤Warning, m.STANCE_WARNING.get(fa), d, achievableOpinion, 0.0).send();
         // END EDIT
      }

      return true;
   }

   private static void messUp(FactionNPC fa, DealBool bool, DipStance stance) {
      if (!fa.request.has()) {
         Deal d = DIP.TMP();
         d.setFactionAndClear(fa);
         bool.set(true);
         double v = -d.valueCredits();
         double b = v * 0.5 + (0.5 + RND.rFloat());
         DealDrawfter.draft(d, b, false, true);
         if (v < d.player.offerableWorth()) {
            KingMessages m = fa.court().king().roy().induvidual.race().kingMessage();
            new UIDipMessDeal(Str.TMP.clear().add(¤¤title).insert(0, stance.name), m.STANCE_UP.get(fa), d, 0.0, -0.1).send();
         }
      }
   }

   private double giftWorth(FactionNPC fa) {
      Deal d = DIP.TMP();
      double min = 2000.0;
      double max = 60000.0;
      max = Math.min(max, d.npc.offerableWorth() * 0.025);
      return max > min ? min + BOOSTABLES.NOBLE().PRIDE.get(fa.king().induvidual) * (max - min) : 0.0;
   }
}
