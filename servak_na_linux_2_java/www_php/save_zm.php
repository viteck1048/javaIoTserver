<?php
include('config/boot.php');


if(isset($_POST['zm']))
{
	
	if($lang === 'uk') {
		$txt1 = "змінна ";
		$txt2 = "перемекач ";
		$txt3 = " збережена.";
		$txt5 = "індекс M_ID або Z_ID";
		$txt6 = " дорівнює нулю.";
	}else if($lang === 'bg') {
		$txt1 = "променлива ";
		$txt2 = "преключвател ";
		$txt3 = " сьхранена.";
		$txt5 = "индекс M_ID или Z_ID";
		$txt6 = " е нула.";
	}else if($lang === 'en') {
		$txt1 = "the variable ";
		$txt2 = " the swich ";
		$txt3 = " is preserved.";
		$txt5 = "index M_ID or Z_ID";
		$txt6 = " is equal to zero.";
	}else {
		$txt1 = "err";
		$txt2 = "err";
		$txt3 = "err";
		$txt5 = "err";
		$txt6 = "err";
	}
	
	$form = $_POST['zm'];
	$infa1 = db_escape($form['BUKVA'] == null ? '' : $form['BUKVA']);
	
	if(strlen(trim($form['BUKVA'])) != 1)
		$status->error("BUKVA != 1");
	if(($form['M_ID'] + 0) == 0 || ($form['Z_ID'] + 0) == 0)
		$status->error($txt5.$txt6);
	if(($form['NPP_S'] + 0) < 0 || ($form['NPP_S'] + 0) > 2)
		$status->error("NPP_S != 0...2");
	
	if($status->success()) {
		if(($form['NPP_S'] + 0) == 0)
			$status->info($txt1.$infa1.$txt3);
		else
			$status->info($txt2.$infa1.$txt3);
		if($form['ZNACHENNJA'] != null) {
			$db->query(sprintf("
				UPDATE ZMINNY SET ZNACHENNJA=%.20f
				WHERE Z_ID=%d",
				($form['ZNACHENNJA'] + 0),
				($form['Z_ID'] + 0)
			));
		}
		else {
			$db->query(sprintf("
				UPDATE ZMINNY SET ZNACHENNJA=null
				WHERE Z_ID=%d",
				($form['Z_ID'] + 0)
			));
		}
		
		if($form['NAME'] != null) {
			$db->query(sprintf("
				UPDATE ZMINNY SET NAME='%s'
				WHERE Z_ID=%d",
				db_escape($form['NAME']),
				($form['Z_ID'] + 0)
			));
		}
		else {
			$db->query(sprintf("
				UPDATE ZMINNY SET NAME=null
				WHERE Z_ID=%d",
				($form['Z_ID'] + 0)
			));
		}
		$db->query(sprintf("
			UPDATE ZMINNY SET NPP_S=%d
			WHERE Z_ID=%d",
			($form['NPP_S'] + 0),
			($form['Z_ID'] + 0)
		));
		
	}
	echo $status->html();
}
