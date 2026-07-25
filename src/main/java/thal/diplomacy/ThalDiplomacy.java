// ThalDiplomacy.java
// Document Version 1.0.5
// Creation date: 2026/07/25
// Creator: Thalassicus

package thal.diplomacy;

import game.boosting.BOOSTABLES;
import game.faction.Faction;
import game.faction.diplomacy.DIP;
import game.faction.diplomacy.DipStance;
import game.faction.diplomacy.deal.DealParty;
import game.faction.npc.FactionNPC;
import game.faction.royalty.Royalty;
import game.time.TIME;
import snake2d.util.misc.CLAMP;
import snake2d.util.rnd.RND;
import world.region.RD;

public final class ThalDiplomacy {

    private static final double POWER_FLOOR = 1000.0;
    private static final double DEMAND_CAP_FRACTION = 0.10;
    private static final double BLUSTER_AT_WAR_START = 1.0;
    private static final double WEARINESS_MAX = 0.5;
    private static final double WEARINESS_CURVE_EXPONENT = 2.0;
    private static final double POPULATION_PER_WAR_YEAR = 20000.0;
    private static final double PEACE_RANDOM_DISCOUNT_MAX = 0.5;
    private static final double EXTORTION_MAX_FRACTION = 0.10;
    private static final double MINIMUM_EXTORTION_LEVERAGE = 0.10;
    private static final double EXTORTION_RANDOM_FLOOR = 0.5;
    private static final double PRIDE_MODIFIER_BASE = 0.8;
    private static final double PRIDE_MODIFIER_RANGE = 0.2;
    private static final double CANCELLATION_THRESHOLD_FRACTION = 0.75;
    private static final double AGREEMENT_SAFETY_MARGIN = 0.25;
    private static final double MINIMUM_RAMP_YEARS = 0.5;
    private static final double MAXIMUM_RAMP_YEARS = 16.0;
    private static final int DAYS_PER_YEAR = 16;

    private ThalDiplomacy() {
    }

    public static double secondsPerYear() {
        return DAYS_PER_YEAR * TIME.secondsPerDay();
    }

    // FactionNPC.citizens(null) reads only the capitol region despite its name.
    public static int realmPopulation(Faction sourceFaction) {
        return RD.RACES().population.faction().get(sourceFaction);
    }

    // UIDipMessDeal voids a drafted peace offer when either side's offensivePower()
    // moves more than 25%, so this must stay on that same metric.
    public static double militaryAdvantage(Faction playerFaction, Faction npcFaction) {
        double playerPower = playerFaction.offensivePower();
        double npcPower = npcFaction.offensivePower();
        double divisor = Math.max(Math.max(playerPower, npcPower), POWER_FLOOR);
        return CLAMP.d((playerPower - npcPower) / divisor, -1.0, 1.0);
    }

    public static double rampYears(Faction npcFaction) {
        double rawYears = realmPopulation(npcFaction) / POPULATION_PER_WAR_YEAR;
        return CLAMP.d(rawYears, MINIMUM_RAMP_YEARS, MAXIMUM_RAMP_YEARS);
    }

    // DIP.secondSinceStance measures the current stance, which is only the war's
    // duration while that stance is war.
    public static double warProgress(FactionNPC npcFaction) {
        if (!DIP.WAR().is(npcFaction)) {
            return 0.0;
        }

        double warYears = DIP.secondSinceStance(npcFaction) / secondsPerYear();
        return CLAMP.d(warYears / rampYears(npcFaction), 0.0, 1.0);
    }

    // Curved rather than linear so the opening days barely move the terms. When a
    // large military advantage nearly cancels the opening bluster, a linear ramp flips
    // the sign of the whole deal within hours of the war being declared.
    public static double negotiationShift(FactionNPC npcFaction) {
        double totalRange = BLUSTER_AT_WAR_START + WEARINESS_MAX;
        double curved = Math.pow(warProgress(npcFaction), WEARINESS_CURVE_EXPONENT);
        return -BLUSTER_AT_WAR_START + curved * totalRange;
    }

    public static double peaceAdvantage(Faction playerFaction, FactionNPC npcFaction) {
        double combined = militaryAdvantage(playerFaction, npcFaction) + negotiationShift(npcFaction);
        return CLAMP.d(combined, -1.0, 1.0);
    }

    // Negative results bill the player, positive results bill the faction.
    // DealParty.init() pre-quarters a non-player party's offerableWorth, so the same
    // fraction takes a smaller share from the faction than it does from the player.
    public static double calculatePeaceValue(DealParty playerParty, DealParty npcParty) {
        FactionNPC npcFaction = npcParty.npc();
        double advantage = peaceAdvantage(playerParty.f(), npcFaction);
        DealParty payingParty = advantage < 0.0 ? playerParty : npcParty;
        return advantage * DEMAND_CAP_FRACTION * payingParty.offerableWorth();
    }

    // DealDrawfter.draft() re-reads valueCredits() and adds this delta to it, so the
    // delta is relative to a peace value that has already been counted once. Scaling
    // the value itself here would double it instead of discounting it.
    public static double randomizedPeaceDelta(double peaceValue) {
        return -peaceValue * RND.rFloat() * PEACE_RANDOM_DISCOUNT_MAX;
    }

    // Maps a military advantage of -1 through +1 onto a full through minimal demand.
    // A dominant player still faces a token request rather than none at all, because
    // accepting is what restores trust and stops the threats recurring, so an empty
    // offer would leave that cycle with no exit.
    public static double extortionLeverage(Faction playerFaction, Faction npcFaction) {
        double advantage = militaryAdvantage(playerFaction, npcFaction);
        double weight = (1.0 - advantage) / 2.0;
        return MINIMUM_EXTORTION_LEVERAGE + (1.0 - MINIMUM_EXTORTION_LEVERAGE) * weight;
    }

    public static double calculateExtortionWorth(DealParty playerParty, Faction npcFaction) {
        double leverage = extortionLeverage(playerParty.f(), npcFaction);
        double variation = EXTORTION_RANDOM_FLOOR + (1.0 - EXTORTION_RANDOM_FLOOR) * RND.rFloat();
        return playerParty.offerableWorth() * EXTORTION_MAX_FRACTION * leverage * variation;
    }

    // Vanilla spread this across 0.25 to 1.0, a fourfold swing driven by a trait the
    // player cannot see or influence. It cancels out of the opinion a gift grants, but
    // still governs how long that gift is remembered and how much OpsGifts overstates
    // the generosity needed to reach a target.
    public static double giftPrideModifier(Royalty royalty) {
        double pride = CLAMP.d(BOOSTABLES.NOBLE().PRIDE.get(royalty.induvidual), 0.0, 1.0);
        return PRIDE_MODIFIER_BASE + PRIDE_MODIFIER_RANGE * pride;
    }

    // Stance.process() cancels an agreement once opinion falls below this fraction of
    // the stance requirement, so a demand only has to clear that line plus enough
    // margin to survive ordinary decay.
    public static double agreementTargetOpinion(DipStance stance) {
        return stance.opinionNeeded * CANCELLATION_THRESHOLD_FRACTION + AGREEMENT_SAFETY_MARGIN;
    }

    public static double proportionalOpinion(double fullOpinion, double requestedWorth, double draftedWorth) {
        if (requestedWorth <= 0.0) {
            return fullOpinion;
        }

        return fullOpinion * CLAMP.d(draftedWorth / requestedWorth, 0.0, 1.0);
    }
}