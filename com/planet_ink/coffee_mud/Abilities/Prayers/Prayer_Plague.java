package com.planet_ink.coffee_mud.Abilities.Prayers;

import com.planet_ink.coffee_mud.interfaces.*;
import com.planet_ink.coffee_mud.common.*;
import com.planet_ink.coffee_mud.utils.*;
import java.util.*;

public class Prayer_Plague extends Prayer
{
	public String ID() { return "Prayer_Plague"; }
	public String name(){ return "Plague";}
	public int quality(){ return MALICIOUS;}
	public int holyQuality(){ return HOLY_EVIL;}
	public String displayText(){ return "(Plague)";}
	protected int canAffectCode(){return Ability.CAN_MOBS;}
	protected int canTargetCode(){return Ability.CAN_MOBS;}
	public Environmental newInstance(){	return new Prayer_Plague();}
	int plagueDown=4;

	public String text(){return "DISEASE";}

	public boolean tick(int tickID)
	{
		if((affected==null)||(!(affected instanceof MOB)))
			return super.tick(tickID);

		if(!super.tick(tickID))
			return false;
		if((--plagueDown)<=0)
		{
			MOB mob=(MOB)affected;
			plagueDown=4;
			if(invoker==null) invoker=mob;
			int dmg=mob.envStats().level()/2;
			if(dmg<1) dmg=1;
			ExternalPlay.postDamage(invoker,mob,this,dmg,Affect.TYP_DISEASE,-1,"<T-NAME> watch(es) <T-HIS-HER> body erupt with a fresh batch of painful oozing sores!");
			if(mob.location()==null) return false;
			MOB target=mob.location().fetchInhabitant(Dice.roll(1,mob.location().numInhabitants(),-1));
			if((target!=null)&&(target!=invoker)&&(target!=mob)&&(target.fetchAffect(ID())==null))
				if(Dice.rollPercentage()>target.charStats().getStat(CharStats.SAVE_DISEASE))
				{
					mob.location().show(target,null,Affect.MSG_OK_VISUAL,"<S-NAME> look(s) seriously ill!");
					maliciousAffect(invoker,target,48,-1);
				}
		}
		return true;
	}

	public void affectCharStats(MOB affected, CharStats affectableStats)
	{
		super.affectCharStats(affected,affectableStats);
		if(affected==null) return;
		affectableStats.setStat(CharStats.CONSTITUTION,3);
		affectableStats.setStat(CharStats.DEXTERITY,3);
	}

	public void unInvoke()
	{
		// undo the affects of this spell
		if((affected==null)||(!(affected instanceof MOB)))
			return;
		MOB mob=(MOB)affected;

		super.unInvoke();

		if(canBeUninvoked())
			mob.tell("The sores on your face clear up.");
	}


	public boolean invoke(MOB mob, Vector commands, Environmental givenTarget, boolean auto)
	{
		MOB target=getTarget(mob,commands,givenTarget);
		if(target==null) return false;

		if(!super.invoke(mob,commands,givenTarget,auto))
			return false;

		boolean success=profficiencyCheck(0,auto);
		if(success)
		{
			// it worked, so build a copy of this ability,
			// and add it to the affects list of the
			// affected MOB.  Then tell everyone else
			// what happened.
			FullMsg msg=new FullMsg(mob,target,this,affectType(auto)|Affect.MASK_MALICIOUS,auto?"":"^S<S-NAME> inflict(s) an unholy plague upon <T-NAMESELF>.^?");
			FullMsg msg2=new FullMsg(mob,target,this,Affect.MSK_CAST_MALICIOUS_VERBAL|Affect.TYP_DISEASE|(auto?Affect.MASK_GENERAL:0),null);
			if((mob.location().okAffect(msg))&&(mob.location().okAffect(msg2)))
			{
				mob.location().send(mob,msg);
				mob.location().send(mob,msg2);
				if((!msg.wasModified())&&(!msg2.wasModified()))
				{
					invoker=mob;
					maliciousAffect(mob,target,48,-1);
					mob.location().show(target,null,Affect.MSG_OK_VISUAL,"<S-NAME> look(s) seriously ill!");
				}
			}
		}
		else
			return maliciousFizzle(mob,target,"<S-NAME> attempt(s) to inflict a plague upon <T-NAMESELF>, but flub(s) it.");


		// return whether it worked
		return success;
	}
}
