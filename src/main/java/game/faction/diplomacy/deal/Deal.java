// Deal.java
// Document Version 1.0.1
// Creation date: 2026/07/25
// Creator: Thalassicus

package game.faction.diplomacy.deal;

import game.faction.FACTIONS;
import game.faction.diplomacy.deal.DealBool;
import game.faction.diplomacy.deal.DealBools;
import game.faction.diplomacy.deal.DealParty;
import game.faction.diplomacy.deal.DealRegs;
import game.faction.npc.FactionNPC;
import game.faction.royalty.opinion.ROPINION;
import init.trade.TR;
import init.trade.TRADABLE;
import thalassicus.diplomacy.ThalDiplomacy;
import util.Debugger;
import util.gui.misc.GBox;
import view.main.VIEW;

public final class Deal {
   public final DealParty player;
   public final DealParty npc;
   public final DealBools bools;
   public boolean clearDeal;
   int dupI = -1;
   private int cvalue;
   private boolean can = false;
   // START EDIT
   // OpsGifts decays a stored gift at a rate proportional to its own size, so gifts
   // worth under roughly a quarter point of opinion sit in the slow-decay regime and
   // wash out within a game year. Vanilla's 25.0 priced an entire player treasury at
   // about that level against a mid-size faction, below the threshold where the
   // accelerating term lets generosity persist.
   private static final double OPINION_PER_WORTH_RATIO = 500.0;
   // END EDIT

   public Deal() {
      DealRegs.RegData data = new DealRegs.RegData();
      this.player = new DealParty(this, data);
      this.npc = new DealParty(this, data);
      this.bools = new DealBools(this.player, this.npc);
   }

   public void setFactionAndClear(FactionNPC faction) {
      this.setFactionAndClear(faction, true);
   }

   public void setFactionAndClear(FactionNPC faction, boolean clearDeal) {
      this.setFactionAndClear(faction, clearDeal, Debugger.dummy);
   }

   public void setFactionAndClear(FactionNPC faction, boolean clearDeal, Debugger d) {
      this.clearDeal = clearDeal;
      this.player.init(FACTIONS.player(), faction, faction);
      this.npc.init(faction, FACTIONS.player(), faction);
      this.bools.init(true, clearDeal, d);
      this.dupI = -1;
   }

   public boolean canBeAccepted() {
      return this.hasDeal() && ((int)this.valueCredits() >= 0 || this.can);
   }

   public double execute(boolean changeOpinion) {
      double v = this.opinionChange();
      this.player.execute();
      this.npc.execute();
      this.bools.execute();
      if (changeOpinion && this.player.f() == FACTIONS.player()) {
         ROPINION.GIFTS().makeDeal(this.npc.npc(), v);
      }

      if (this.npc.npc().isActive()) {
         this.setFactionAndClear(this.npc.npc(), this.clearDeal);
      }

      return v;
   }

   public double valueCredits() {
      this.can = false;
      this.dupI = VIEW.RI();
      // START EDIT
      // Every consumer of a peace deal reads its value through this method, so
      // swapping the term here reaches both the offers factions send and the ones
      // drafted in the diplomacy screen. DealBools.value() aggregates all bools at
      // once and offers no seam for a single one, hence the subtract-then-add.
      double boolsValue = this.bools.value();
      if (this.bools.PEACE.is()) {
         boolsValue -= this.bools.PEACE.value();
         boolsValue += ThalDiplomacy.calculatePeaceValue(this.player, this.npc);
      }

      this.cvalue = (int)boolsValue;
      // END EDIT
      this.cvalue = (int)(this.cvalue + this.player.value());
      this.cvalue = (int)(this.cvalue - this.npc.value());
      return this.cvalue;
   }

   public boolean hasDeal() {
      for (DealBool b : this.bools.all()) {
         if (b.is()) {
            return true;
         }
      }

      return this.has(this.npc) || this.has(this.player);
   }

   public boolean has(DealParty p) {
      if (p.credits.get() != 0) {
         return true;
      }

      for (DealRegs.DealReg r : this.player.regs.all()) {
         if (r.is()) {
            return true;
         }
      }

      for (TRADABLE r : TR.ALL()) {
         if (p.resources.get(r) != 0) {
            return true;
         }
      }

      return false;
   }

   public double opinionChange() {
      return this.opinionChangeD();
   }

   // START EDIT
   public double opinionChangeD() {
      double c = OPINION_PER_WORTH_RATIO * this.valueCredits();
      return c / this.npc.selfWorth();
   }

   public double getWorthOfOpinion(double opinion) {
      return opinion * this.npc.selfWorth() / OPINION_PER_WORTH_RATIO;
   }
   // END EDIT

   public double betrayal() {
      return this.bools.betrayal();
   }

   public void hoverBetrayal(GBox b) {
      this.bools.betrayalHover(b);
   }
}
