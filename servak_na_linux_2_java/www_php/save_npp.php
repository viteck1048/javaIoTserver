<?php
include('config/boot.php');

if(isset($_POST['npp']))
{
	if($lang === 'uk') {
		$txt1 = "умова ";
		$txt5 = "коментар ";
		$txt2 = " збережена.";
		$txt3 = " має меньше 3-х символів.";
		$txt4 = "індекс N_ID або Z_ID";
		$txt6 = " дорівнює нулю.";
		$txt7 = "константа ";
	}else if($lang === 'bg') {
		$txt1 = "условието ";
		$txt5 = "коментарий ";
		$txt2 = " сьхранена.";
		$txt3 = " има по малко от 3 символа.";
		$txt4 = "индекс N_ID или Z_ID";
		$txt6 = " е нула.";
		$txt7 = "константа ";
	}else if($lang === 'en') {
		$txt1 = "the condition ";
		$txt5 = "the coment ";
		$txt2 = " is preserved.";
		$txt3 = " should be more than 3 character.";
		$txt4 = "index N_ID or Z_ID";
		$txt6 = " is equal to zero.";
		$txt7 = "the constant ";
	}else {
		$txt1 = "err";
		$txt5 = "err";
		$txt2 = "err";
		$txt3 = "err";
		$txt4 = "err";
		$txt6 = "err";
		$txt7 = "err";
	}
	
	$form = $_POST['npp'];
	$infa = db_escape($form['UMOVA'] == null ? '' : $form['UMOVA']);
	$infa2 = db_escape($form['COMENT'] == null ? '' : $form['COMENT']);
	if(strlen(trim($form['UMOVA'])) < 3)
		$status->error($txt1.$infa.$txt3);
	if(strlen(trim($form['COMENT'])) < 1)
		$status->error($txt5.$infa2.$txt3);
	if(($form['N_ID'] + 0) == 0 || ($form['Z_ID'] + 0) == 0)
		$status->error($txt4.$txt6);
	if($form['ZNACHENNJA'] == null || ($form['ZNACHENNJA'] + 0) == 0)
		$status->error($txt7.$infa2.$txt6);
	
	if($status->success()) {
		$status->info($txt7.$infa2.$txt2);
		
		$db->query(sprintf("
			UPDATE ZMINNY_NPP SET UMOVA='%s', COMENT='%s', ZNACHENNJA=%.20f
			WHERE N_ID=%d",
			db_escape($form['UMOVA']),
			db_escape($form['COMENT']),
			($form['ZNACHENNJA'] + 0),
			($form['N_ID'] + 0)
		));
		
	}
	echo $status->html();
}
