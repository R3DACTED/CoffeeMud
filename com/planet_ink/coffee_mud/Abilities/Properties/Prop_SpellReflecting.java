package com.planet_ink.coffee_mud.Abilities.Properties;

import com.planet_ink.coffee_mud.interfaces.*;
import com.planet_ink.coffee_mud.common.*;
import com.planet_ink.coffee_mud.utils.*;
import java.util.*;

public class Prop_SpellReflecting extends Property
{
	public String ID() { return "Prop_SpellReflecting"; }
	public String name(){ return "Spell reflecting property";}
	protected int canAffectCode(){return Ability.CAN_MOBS|Ability.CAN_ITEMS;}
	public Environmental newInstance(){	Prop_SpellReflecting BOB=new Prop_SpellReflecting();	BOB.setMiscText(text());return BOB;}

	protected int minLevel=1;
	protected int maxLevel=30;
	protected int chance=100;
	protected int remaining=100;
	protected int fade=1;
	protected int uses=100;
	protected long lastFade=0;
	
	public int usesRemaining(){return uses;}
	public void setUsesRemaining(int newUses){uses=newUses;}
	
	public void setMiscText(String newText)
	{
		super.setMiscText(newText);
		minLevel=getParmVal(newText,"min",minLevel);
		maxLevel=getParmVal(newText,"max",maxLevel);
		chance=getParmVal(newText,"chance",chance);
		fade=getParmVal(newText,"fade",fade);
		remaining=getParmVal(newText,"remain",remaining);
		setUsesRemaining(remaining);
	}

	public boolean okAffect(Affect affect)
	{
		if(affected==null)	return true;
		if((fade<=0)&&(usesRemaining()<remaining))
		{
			if(lastFade==0) lastFade=System.currentTimeMillis();
			long time=System.currentTimeMillis()-lastFade;
			if(time>5*60000)
			{
				double div=Util.div(time,(long)5*60000);
				if(div>1.0)
				{
					setUsesRemaining(usesRemaining()+(int)Math.round(div));
					if(usesRemaining()>remaining)
						setUsesRemaining(remaining);
					lastFade=System.currentTimeMillis();
				}
			}
		}
		
		if((Util.bset(affect.targetCode(),Affect.MASK_MALICIOUS))
		&&(affect.targetMinor()==Affect.TYP_CAST_SPELL)
		&&(affect.tool()!=null)
		&&(affect.tool() instanceof Ability)
		&&(Dice.rollPercentage()<=chance)
		&&(usesRemaining()>0)
		&&((((Ability)affect.tool()).classificationCode()&Ability.ALL_CODES)==Ability.SPELL))
		{
			MOB target=null;
			if(affected instanceof MOB) 
				target=(MOB)affected;
			else
			if((affected instanceof Item)
			&&(!((Item)affected).amWearingAt(Item.INVENTORY))
			&&(((Item)affected).owner()!=null)
			&&(((Item)affected).owner() instanceof MOB))
				target=(MOB)((Item)affected).owner();
			else
				return true;
			
			if(!affect.amITarget(target)) return true;
			if(affect.amISource(target)) return true;
			if(target.location()==null) return true;
			
			int lvl=CMAble.qualifyingLevel(affect.source(),((Ability)affect.tool()));
			if(lvl<=0) lvl=CMAble.lowestQualifyingLevel(((Ability)affect.tool()).ID());
			if(lvl<=0) lvl=1;
			if((lvl<minLevel)||(lvl>maxLevel)) return true;

			target.location().show(target,affected,Affect.MSG_OK_VISUAL,"The field around <T-NAMESELF> reflects the spell!");
			Ability A=(Ability)affect.tool();
			A.invoke(target,affect.source(),true);
			setUsesRemaining(usesRemaining()-lvl);
			if(usesRemaining()<=0)
			{
				if(affected instanceof MOB)
				{
					target.location().show(target,target,Affect.MSG_OK_VISUAL,"The field around <T-NAMESELF> fades.");
					if(fade>0)
						target.delAffect(this);
				}
				else
				if(affected instanceof Item)
				{
					if(fade>0)
					{
						target.location().show(target,affected,Affect.MSG_OK_VISUAL,"<T-NAMESELF> vanishes!");
						((Item)affected).destroyThis();
						target.location().recoverRoomStats();
					}
					else
						target.location().show(target,affected,Affect.MSG_OK_VISUAL,"The field around <T-NAMESELF> fades.");
				}
			}
			return false;
		}
		return super.okAffect(affect);
	}


}
