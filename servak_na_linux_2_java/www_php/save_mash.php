<?php
include('config/boot.php');


if(isset($_POST['mash']))
{
	
	if($lang === 'uk') {
		$txt1 = "верстат ";
		$txt2 = " має меньше 3-х символів.";
		$txt3 = " збережено.";
		$txt6 = "ім'я верстата ";
		$txt7 = "перелік коліс ";
		$txt8 = "налічує непідтримувані символи.";
	}else if($lang === 'bg') {
		$txt1 = "лира ";
		$txt2 = " има по малко от 3 символа.";
		$txt3 = " сьхранена.";
		$txt6 = "името на машина ";
		$txt7 = "поле 'зьбни колела' ";
		$txt8 = "сьдьржа букви";
	}else if($lang === 'en') {
		$txt1 = "the mashine ";
		$txt2 = " should be more than 3 character.";
		$txt3 = " is preserved.";
		$txt6 = "";
		$txt7 = "list of gears ";
		$txt8 = "has characters.";
	}else {
		$txt1 = "err";
		$txt2 = "err";
		$txt3 = "err";
		$txt6 = "err";
		$txt7 = "err";
		$txt8 = "err";
	}
	
	$form = $_POST['mash'];
	$infa1 = db_escape($form['NAME'] == null ? '' : $form['NAME']);
	
	if(strlen(trim($form['NAME'])) < 3)
		$status->error($txt6.$infa1.$txt2);
	if(strlen(trim($form['M1'])) < 3)
		$status->error($txt7.$txt2);
	if(!preg_match('/^[0-9\s]+$/', $form['M1']))
		$status->error($txt7." 1 ".$txt8);
	if($form['M2'] != null && strlen(trim($form['M2'])) > 0) {
		if(!preg_match('/^[0-9\s]+$/', $form['M2']))
			$status->error($txt7." 2 ".$txt8);
	}
	
	if($status->success()) {
		$status->info($txt1.$infa1.$txt3);
			
		if($form['M2'] == null) {
			$db->query(sprintf(
				"UPDATE MASHYNES SET NAME='%s', M1='%s', M2=null
				WHERE M_ID=%d",
				db_escape($form['NAME']),
				db_escape($form['M1']),
				($form['M_ID'] + 0)
			));
		}else {
			$db->query(sprintf(
				"UPDATE MASHYNES SET NAME='%s', M1='%s', M2='%s'
				WHERE M_ID=%d",
				db_escape($form['NAME']),
				db_escape($form['M1']),
				db_escape($form['M2']),
				($form['M_ID'] + 0)
			));
		}
	}
	echo $status->html();
}
