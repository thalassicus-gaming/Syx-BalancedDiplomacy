package game.faction.royalty.opinion;

import game.GAME;
import game.faction.FACTIONS;
import game.faction.npc.FactionNPC;
import game.faction.player.emmi.EmiTypeRoy;
import game.faction.royalty.Royalty;
import game.faction.royalty.opinion.ROPINION;
import game.faction.royalty.opinion.ROpper;
import game.time.TIME;
import init.sprite.UI.UI;
import settlement.stats.Induvidual;
import settlement.stats.STATS;
import snake2d.util.datatypes.DIR;
import snake2d.util.gui.GuiSection;
import snake2d.util.sprite.text.Str;
import util.text.D;
import view.ui.message.MessageSection;
import view.world.ui.faction.UIRoyalty;

public final class OpsEmi {
   private static CharSequence ¤¤flattery = "Flattery";
   private static CharSequence ¤¤dflatteryD = "Flattery From your Emissaries.";
   private static CharSequence ¤¤sabotage = "Sabotage";
   private static CharSequence ¤¤sabotageD = "Sabotage from your Emissaries.";
   private static CharSequence ¤¤assasination = "Assassinations";
   private static CharSequence ¤¤assasinationD = "Assassinations of court members.";
   private static CharSequence ¤¤assasinated = "Assassinated!";
   private static CharSequence ¤¤assasinatedSucc = "Our emissaries report, their mission is done. The great lord of {FACTION} slipped last night on their nightdress, leading to a fall down the stone stairs of {HIS} bed chamber. Once down, a chandelier happened to fall on top of {NAME}'s head, crushing the skull completely. What a tragedy!";
   private static CharSequence ¤¤assasinatedFail = "Busted!";
   private static CharSequence ¤¤assasinatedFailD = "One of our emissaries serving in the court of {FACTION} was arrested and tortured. Unfortunately, our plans have been compromised. {NAME} knows this, and is not too happy about it. Our 'attempts' will continue, but it will be harder now.";
   private final ROpper good;
   private final ROpper bad;
   private final ROpper assas;

   static {
      D.ts(OpsEmi.class);
   }

   OpsEmi() {
      final double year = 16 * TIME.secondsPerDay();
      this.good = new ROpper("EMMI_GOOD", ¤¤flattery, ¤¤dflatteryD, UI.icons().s.gift, 80.0, false) {
         @Override
         public double increase(Royalty roy) {
            double v = this.value.getD(roy);
            double target = this.ptarget(roy);
            if (target > v) {
               return 1.0 / (year * 2.0);
            } else {
               return target < v ? -1.0 / (year * 0.5) : 0.0;
            }
         }

         @Override
         protected double ptarget(Royalty bo) {
            return OpsEmi.this.vv(bo, FACTIONS.player().emissaries.flatter, this, FACTIONS.player().emissaries.penaltyMul());
         }
      };
      this.bad = new ROpper("EMMI_BAD", ¤¤sabotage, ¤¤sabotageD, UI.icons().s.gift, -160.0, false) {
         @Override
         public double increase(Royalty roy) {
            double v = this.value.getD(roy);
            double target = this.ptarget(roy);
            if (target > v) {
               return 1.0 / (year * 2.0);
            } else {
               return target < v ? -1.0 / (year * 0.5) : 0.0;
            }
         }

         @Override
         protected double ptarget(Royalty bo) {
            return OpsEmi.this.vv(bo, FACTIONS.player().emissaries.sabotage, this, FACTIONS.player().emissaries.penaltyMul());
         }
      };
      this.assas = new ROpper.ROpperDown("EMMI_ASSES", ¤¤assasination, ¤¤assasinationD, UI.icons().s.death, -10.0, false, year * 4.0) {
         @Override
         public void update(Royalty roy, double time) {
            double t = OpsEmi.this.assasinationsPerYear(roy, FACTIONS.player().emissaries.penaltyMul());
            t = time * t / year;
            int a = (int)this.state.getD(roy);
            this.state.incD(roy, t);
            int n = (int)this.state.getD(roy);
            if (a != n) {
               this.state.incD(roy, -n);
               long ran = STATS.RAN().getL(roy.induvidual, a % 32);
               if ((ran & 3L) == 0L) {
                  OpsEmi.this.assasinate(roy, true);
                  GAME.count().ROYALTIES_KILLED.inc(1);
               } else {
                  OpsEmi.this.assasinate(roy, false);
               }
            }

            super.update(roy, time);
         }
      };
   }

   private double vv(Royalty roy, EmiTypeRoy em, ROpper op, double eff) {
      return em.get(roy) * this.valuePerEmissary(roy.court.faction) * eff / Math.abs(op.to());
   }

   private double valuePerEmissary(FactionNPC f) {
       // START MOD CHANGE: was 768000.
      return 1000000.0 / FACTIONS.WORTH().faction(f);
      // END MOD CHANGE
   }

   public void assasinate(Royalty roy, boolean kill) {
      this.assas.value.incD(roy, 0.25);
      if (kill) {
         roy.kill(false);
         new Mess(¤¤assasinated, ¤¤assasinatedSucc, roy).send();
      } else {
         new Mess(¤¤assasinatedFail, ¤¤assasinatedFailD, roy).send();
      }
   }

   public double assasinationsPerYear(Royalty roy, double efficiency) {
      double t = FACTIONS.player().emissaries.assasinate.get(roy) * this.valuePerEmissary(roy.court.faction) * FACTIONS.player().emissaries.penaltyMul();
      return t / (1.0 + this.assas.value.getD(roy) * 4.0);
   }

   public double opinionTarget(Royalty roy, double efficiency) {
      double oldg = this.good.value.getD(roy);
      double oldb = this.bad.value.getD(roy);
      this.good.value.setD(roy, this.vv(roy, FACTIONS.player().emissaries.flatter, this.good, efficiency));
      this.bad.value.setD(roy, this.vv(roy, FACTIONS.player().emissaries.sabotage, this.bad, efficiency));
      double res = ROPINION.get(roy);
      this.good.value.setD(roy, oldg);
      this.bad.value.setD(roy, oldb);
      return res;
   }

   public double trustTarget(Royalty roy, double efficiency) {
      double oldg = this.good.value.getD(roy);
      double oldb = this.bad.value.getD(roy);
      this.good.value.setD(roy, this.vv(roy, FACTIONS.player().emissaries.flatter, this.good, efficiency));
      this.bad.value.setD(roy, this.vv(roy, FACTIONS.player().emissaries.sabotage, this.bad, efficiency));
      double res = ROPINION.trust().get(roy.court.faction);
      this.good.value.setD(roy, oldg);
      this.bad.value.setD(roy, oldb);
      return res;
   }

   private static class Mess extends MessageSection {
      private static final long serialVersionUID = 1L;
      private final String desc;
      private final Induvidual indu;
      private final String name;
      private final String fName;
      private int sI;

      public Mess(CharSequence title, CharSequence desc, Royalty roy) {
         super(title);
         this.desc = desc + "";
         this.name = roy.name() + "";
         this.indu = roy.induvidual;
         this.sI = roy.successionI();
         this.fName = roy.court.faction.name + "";
      }

      @Override
      protected void make(GuiSection section) {
         this.paragraph(
            Str.TMP
               .clear()
               .add(this.desc)
               .insert("NAME", this.name)
               .insert("FACTION", this.fName)
               .insert("HIS", this.indu.race().info.pHIS.get(this.indu, false))
         );
         section.addRelBody(8, DIR.N, new UIRoyalty.PortraitAbs(4) {
            @Override
            protected int succ() {
               return Mess.this.sI;
            }

            @Override
            protected Induvidual indu() {
               return Mess.this.indu;
            }
         });
      }
   }
}
