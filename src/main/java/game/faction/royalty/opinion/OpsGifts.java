// OpsGifts.java
// Document Version 1.0.0
// Creation date: 2026/07/25
// Creator: Thalassicus

package game.faction.royalty.opinion;

import game.faction.npc.FactionNPC;
import game.faction.royalty.Royalty;
import game.time.TIME;
import init.sprite.UI.UI;
import thalassicus.diplomacy.ThalDiplomacy;
import util.text.D;

public final class OpsGifts {
   private static CharSequence ¤¤name = "Generosity";
   private static CharSequence ¤¤nameE = "Extortion";
   private static CharSequence ¤¤nameD = "Based on your previous dealings and gifts.";
   private final ROpper.ROpperDown op;
   private final ROpper.ROpperDown ex;

   static {
      D.ts(OpsGifts.class);
   }

   OpsGifts() {
      double year = TIME.secondsPerDay() * 16;
      this.op = new ROpper.ROpperDown("DEALINGS", ¤¤name, ¤¤nameD, UI.icons().s.happy, 100.0, false, year * 10.0 * 100.0) {
         // START EDIT
         public double getModifier(Royalty roy) {
            return ThalDiplomacy.giftPrideModifier(roy);
         }
         // END EDIT

         @Override
         public double increase(Royalty roy) {
            return (1.0 + 99.0 * this.value.getD(roy)) * super.increase(roy);
         }
      };
      this.ex = new ROpper.ROpperDown("DEALINGSE", ¤¤nameE, ¤¤nameD, UI.icons().s.happy, -100.0, false, year * 10.0 * 100.0) {
         // START EDIT
         public double getModifier(Royalty roy) {
            return ThalDiplomacy.giftPrideModifier(roy);
         }
         // END EDIT

         @Override
         public double increase(Royalty roy) {
            return (1.0 + 99.0 * this.value.getD(roy)) * super.increase(roy);
         }
      };
   }

   public double getGenerosityNeededForPeace(FactionNPC f) {
      return (ROPINION.getPeaceValue(f, this.op, 1.0) - this.op.value.getD(f.king())) * this.op.to();
   }

   public double getGenerosityNeededForOpinion(FactionNPC f, double target) {
      return (ROPINION.getOpinionValue(f, this.op, target) - this.op.value.getD(f.king())) * this.op.to();
   }

   public void makeDeal(FactionNPC f, double generousity) {
      for (Royalty r : f.court().all()) {
         this.makeDeal(r, r.isKing() ? generousity : generousity * 0.25);
      }
   }

   private void makeDeal(Royalty roy, double generousity) {
      if (generousity < 0.0) {
         this.ex.value.incD(roy, generousity / (this.ex.to() * this.ex.getModifier(roy)));
      } else {
         this.op.value.incD(roy, generousity / (this.op.to() * this.op.getModifier(roy)));
      }
   }
}
