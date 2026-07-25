// Peace.java
// Document Version 1.0.0
// Creation date: 2026/07/25
// Creator: Thalassicus

package game.events.faction.player;

import game.faction.diplomacy.DIP;
import game.faction.diplomacy.deal.Deal;
import game.faction.diplomacy.deal.DealDrawfter;
import game.faction.npc.FactionNPC;
import init.race.KingMessages;
import settlement.main.SETT;
import snake2d.util.rnd.RND;
import thal.diplomacy.ThalDiplomacy;
import util.text.Dic;
import view.ui.diplomacy.UIDipMessDeal;
import world.army.AD;

class Peace {
   boolean update() {
      if (SETT.INVADOR().invading()) {
         return false;
      }

      if (!RND.oneIn(6)) {
         return true;
      }

      FactionNPC f = null;

      for (FactionNPC ff : DIP.WAR().player()) {
         if (f == null || AD.power().get(ff) > AD.power().get(f)) {
            f = ff;
         }
      }

      if (f != null && !f.request.has()) {
         KingMessages m = f.court().king().roy().induvidual.race().kingMessage();
         Deal d = DIP.TMP();
         d.setFactionAndClear(f);
         d.bools.PEACE.set(true);
         CharSequence desc = null;
         double credits = d.valueCredits();
         if (credits > 0.0) {
            desc = m.PEACE_GOOD.get(f);
         } else {
            desc = m.PEACE_BAD.get(f);
         }

         // START EDIT
         credits = ThalDiplomacy.randomizedPeaceDelta(credits);
         // END EDIT
         DealDrawfter.draft(d, credits, true, true);
         new UIDipMessDeal(Dic.¤¤peace, desc, d, 0.5, -0.5).send();
         return true;
      } else {
         return false;
      }
   }
}
