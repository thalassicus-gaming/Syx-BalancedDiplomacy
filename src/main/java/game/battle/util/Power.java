package game.battle.util;

import game.GAME;
import game.battle.div.Div;
import game.battle.util.DIV_SPEC;
import game.battle.util.DivType;
import game.boosting.BOOSTABLES;
import game.boosting.Boostable;
import game.faction.FACTIONS;
import game.faction.Faction;
import init.race.RACES;
import init.race.Race;
import java.nio.file.Path;
import settlement.stats.STATS;
import settlement.stats.colls.StatsBattle;
import settlement.stats.equip.EquipBattle;
import settlement.stats.equip.EquipRange;
import snake2d.util.gui.GUI_BOX;
import snake2d.util.misc.ACTION;
import util.gui.misc.GBox;
import util.gui.misc.GText;
import util.info.GFORMAT;
import util.text.D;
import util.text.Dic;
import world.entity.army.WArmy;

public final class Power {
   public static CharSequence ¤¤desc = "The overall power of a battle unit. Divided into different attack and defence types. The total power is an indication of how well the unit will perform in a fight, but in practice each type determines the outcome.";
   private static CharSequence ¤¤attack = "attack";
   private static CharSequence ¤¤defence = "defence";
   private static CharSequence ¤¤morale = "morale";
   private static CharSequence ¤¤mass = "mass";
   private static CharSequence ¤¤speed = "speed";
   private static CharSequence ¤¤charge = "charge";
   private static CharSequence ¤¤ranged = "ranged";
   public final double HIGH_POWER = 5.0;
   private double minPower = -1.0;
   private double maxPI = -1.0;
   private double bestRanged = 1.0;
   private Div sDiv;
   private final DIV_SPEC dstats = new DIV_SPEC() {
      @Override
      public double training(StatsBattle.StatTraining tr) {
         return tr.stat.div().getD(Power.this.sDiv);
      }

      @Override
      public double equip(EquipBattle e) {
         return e.stat().div().getD(Power.this.sDiv);
      }

      @Override
      public Race race() {
         return Power.this.sDiv.info.race();
      }

      @Override
      public int men() {
         return Power.this.sDiv.menNrOf();
      }

      @Override
      public Faction faction() {
         return Power.this.sDiv.army().faction();
      }

      @Override
      public double experience() {
         return STATS.BATTLE().COMBAT_EXPERIENCE.div().getD(Power.this.sDiv);
      }

      @Override
      public CharSequence name() {
         return null;
      }

      @Override
      public int bannerI() {
         return 0;
      }
   };

   static {
      D.ts(Power.class);
   }

   Power() {
      GAME.saver().onAfterLoad(new ACTION.ACTION_O<Path>() {
         public void exe(Path t) {
            Power.this.maxPI = -1.0;
         }
      });
   }

   public double get(DIV_SPEC div) {
      this.init();
      double d = this.pget(div);
      d -= this.minPower;
      d *= this.maxPI;
      return div.men() * (1.0 + d);
   }

   public void hover(GUI_BOX box, DIV_SPEC spec) {
      GBox b = (GBox)box;
      b.title(Dic.¤¤Power);
      b.text(¤¤desc);
      double att = this.attack(spec);
      double def = this.defence(spec);
      b.NL(8);
      b.textLL(¤¤attack);
      b.tab(6);
      GText t = b.text();
      t.add('+');
      b.add(GFORMAT.f(t, att));
      b.NL();
      b.textLL(¤¤defence);
      b.tab(6);
      t = b.text();
      t.add('+');
      b.add(GFORMAT.f(t, def));
      b.NL();
      b.textLL(¤¤morale);
      b.tab(6);
      t = b.text();
      t.add('*');
      b.add(GFORMAT.f(t, this.bo(spec, BOOSTABLES.BATTLE().MORALE)));
      b.NL();
      b.textLL(¤¤charge);
      b.tab(6);
      t = b.text();
      t.add('+');
      b.add(GFORMAT.f(t, att * this.bo(spec, BOOSTABLES.BATTLE().CHARGE) / 2.0));
      b.NL();
      b.textLL(¤¤mass);
      b.tab(6);
      t = b.text();
      t.add('*');
      b.add(GFORMAT.f(t, 1.0 + 0.1 * this.bo(spec, BOOSTABLES.PHYSICS().MASS)));
      b.NL();
      b.textLL(¤¤speed);
      b.tab(6);
      t = b.text();
      t.add('*');
      b.add(GFORMAT.f(t, 1.0 + 0.1 * this.bo(spec, BOOSTABLES.PHYSICS().SPEED)));
      b.NL();
      b.textLL(¤¤ranged);
      b.tab(6);
      t = b.text();
      t.add('+');
      b.add(GFORMAT.f(t, this.range(spec)));
      b.NL();
      b.textLL(Dic.¤¤Soldiers);
      b.tab(6);
      t = b.text();
      t.add('*');
      b.add(GFORMAT.i(t, spec.men()));
      b.NL();
      b.tab(6);
      t = b.text();
      b.add(GFORMAT.f0(t, this.get(spec)));
      b.NL();
      b.NL();
   }

   private double pget(DIV_SPEC div) {
      double attack = this.attack(div);
      double defence = this.defence(div) + this.defenceDir(div) * 0.5;
      double tot = attack + defence;
      tot *= this.bo(div, BOOSTABLES.BATTLE().MORALE);
      tot += attack * GAME.battle().boost(div, BOOSTABLES.BATTLE().CHARGE) / 2.0;
      tot *= 1.0 + 0.1 * (this.bo(div, BOOSTABLES.PHYSICS().MASS) - 1.0);
      tot *= 1.0 + 0.25 * (this.bo(div, BOOSTABLES.PHYSICS().SPEED) - 1.0);
      tot += this.range(div);
       // MOD START: AI will treat the player's military power as 50% more threatening.
       // DOES NOT change the underlying combat stats of the division. Only the perceived power.
       // Since normalization assumes the faction is the player, and this change happens pre-normalization,
       // the player's stats will not appear to change. Instead, AI power estimates will drop.
       //
       // Key effects:
       // - 50% stronger opponents will become rivals.
       // - Garrisons must be 50% stronger before doing a sortie to attack the player.
       // - AIs will only engage the player's army if it's 50% stronger.
      if (div.faction() == FACTIONS.player()) {tot *= 1.5;}
      // MOD END
      return tot;
   }

   private double attack(DIV_SPEC div) {
      double base = this.bo(div, BOOSTABLES.BATTLE().OFFENCE) + this.bo(div, BOOSTABLES.BATTLE().DEXTERITY) * 0.5;
      double blunt = this.bo(div, BOOSTABLES.BATTLE().BLUNT_ATTACK);
      double res = blunt;

      for (int di = 0; di < BOOSTABLES.BATTLE().DAMAGES.size(); di++) {
         res += blunt * this.bo(div, BOOSTABLES.BATTLE().DAMAGES.get(di).attack) / BOOSTABLES.BATTLE().DAMAGES.size();
      }

      return res + base;
   }

   private double defence(DIV_SPEC div) {
      double base = this.bo(div, BOOSTABLES.BATTLE().DEFENCE)
         + this.bo(div, BOOSTABLES.BATTLE().FORMATION) * 0.5
         + this.bo(div, BOOSTABLES.BATTLE().PARRY) * 0.5;
      double blunt = this.bo(div, BOOSTABLES.BATTLE().BLUNT_DEFENCE);
      double res = 1.0;

      for (int di = 0; di < BOOSTABLES.BATTLE().DAMAGES.size(); di++) {
         res += this.bo(div, BOOSTABLES.BATTLE().DAMAGES.get(di).defence) / BOOSTABLES.BATTLE().DAMAGES.size();
      }

      res *= blunt;
      return res + base;
   }

   private double defenceDir(DIV_SPEC div) {
      double base = this.bo(div, BOOSTABLES.BATTLE().DEFENCE) + this.bo(div, BOOSTABLES.BATTLE().FORMATION) * 0.5;
      double blunt = this.bo(div, BOOSTABLES.BATTLE().BLUNT_DEFENCE_DIR);
      double res = 1.0;

      for (int di = 0; di < BOOSTABLES.BATTLE().DAMAGES.size(); di++) {
         res += this.bo(div, BOOSTABLES.BATTLE().DAMAGES.get(di).defenceDir) / BOOSTABLES.BATTLE().DAMAGES.size();
      }

      res *= blunt;
      return res + base;
   }

   private double range(DIV_SPEC div) {
      EquipRange rr = this.best(div);
      if (rr == null) {
         return 0.0;
      }

      double ref = rr.ref(div.equip(rr), GAME.battle().boost(div, rr.boostable));
      return this.range(rr, ref);
   }

   public double range(EquipRange rr, double ref) {
      double hits = rr.projectile.range(0, ref) / (rr.projectile.reloadSeconds(ref) * 64.0 * (1.0 + BOOSTABLES.PHYSICS().SPEED.baseValue * 4.0));
      double base = hits;
      double blunt = rr.projectile.bluntDamage(ref) / (1.0 + BOOSTABLES.BATTLE().BLUNT_ATTACK.baseValue);
      double res = blunt;

      for (int di = 0; di < BOOSTABLES.BATTLE().DAMAGES.size(); di++) {
         res += blunt * (rr.projectile.damage(di, ref) / ((1.0 + BOOSTABLES.BATTLE().DAMAGES.get(di).attack.baseValue) * BOOSTABLES.BATTLE().DAMAGES.size()));
      }

      res *= base;
      res *= 1.0 + rr.projectile.areaAttack(ref);
      return res * (0.2 + 0.8 * rr.projectile.accuracy(ref));
   }

   private double bo(DIV_SPEC div, Boostable b) {
      return GAME.battle().boost(div, b) / (1.0 + b.baseValue);
   }

   private EquipRange best(DIV_SPEC div) {
      double max = 0.0;
      EquipRange b = null;

      for (int ei = 0; ei < STATS.EQUIP().RANGED().size(); ei++) {
         EquipRange rr = STATS.EQUIP().RANGED().get(ei);
         if (div.equip(rr) > 0.0) {
            double ref = rr.ref(div.equip(rr), GAME.battle().boost(div, rr.boostable));
            double m = this.range(rr, ref);
            if (m > max) {
               max = m;
               b = rr;
            }
         }
      }

      return b;
   }

   public double bestRangedPower() {
      return this.bestRanged;
   }

   private void init() {
      if (!(this.maxPI >= 0.0)) {
         DIV_SPEC.DIV_SPECImp spec = new DIV_SPEC.DIV_SPECImp();
         double minAverage = 0.0;

         for (int ri = 0; ri < RACES.playable().size(); ri++) {
            Race r = RACES.playable().get(ri);
            spec.clear(r);
            spec.menSet(1);
            minAverage += this.pget(spec);
         }

         minAverage /= RACES.playable().size();
         this.minPower = minAverage;
         double highAverage = 0.0;

         for (int ri = 0; ri < RACES.playable().size(); ri++) {
            Race r = RACES.playable().get(ri);
            spec.clear(r);
            spec.menSet(1);
            spec.experienceSet(0.5);
            double am = 0.0;
            double pp = 0.0;

            for (int si = 0; si < GAME.battle().types.ALL().size(); si++) {
               DivType t = GAME.battle().types.ALL().get(si);
               if (t.valid(r)) {
                  am += t.occurence;
                  spec.copySettings(t);
                  pp += this.pget(spec) * t.occurence;
               }
            }

            double m = minAverage;
            if (am > 0.0) {
               m = Math.max(m, pp / am);
            }

            highAverage += m;
         }

         highAverage /= RACES.playable().size();
         double delta = highAverage - minAverage;
         this.maxPI = 5.0 / delta;
         this.bestRanged = 0.0;

         for (EquipRange r : STATS.EQUIP().RANGED()) {
            this.bestRanged = Math.max(this.bestRanged, this.range(r, r.ref(1.0, GAME.battle().boostMax(r.boostable))));
         }
      }
   }

   public double get(Div div) {
      this.sDiv = div;
      return this.get(this.dstats);
   }

   public double get(WArmy a) {
      int am = 0;

      for (int di = 0; di < a.divs().size(); di++) {
         am = (int)(am + this.get(a.divs().get(di)));
      }

      return am;
   }
}
