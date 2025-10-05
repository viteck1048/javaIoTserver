<?php
include('config/boot.php');

if($lang === 'uk') {
	$txt1 = "умова ";
	$txt2 = " збережена.";
	$txt3 = " має меньше 3-х символів.";
	$txt4 = "індекс U_ID або L_ID дорівнює нулю.";
}else if($lang === 'bg') {
	$txt1 = "условието ";
	$txt2 = " сьхранено.";
	$txt3 = " има по малко от 3 символа.";
	$txt4 = "индекс U_ID или L_ID е нула.";
}else if($lang === 'en') {
	$txt1 = "the condition ";
	$txt2 = " is preserved.";
	$txt3 = " should be more than 3 character.";
	$txt4 = "index U_ID or L_ID is equal to zero.";
}else {
	$txt1 = "err";
	$txt2 = "err";
	$txt3 = "err";
	$txt4 = "err";
}

if(isset($_POST['usl']))
{
	$form = $_POST['usl'];
	$infa = db_escape($form['UMOVA'] == null ? '' : $form['UMOVA']);
	if(strlen(trim($form['UMOVA'])) < 3)
		$status->error($txt1.$infa.$txt3);
	if(($form['U_ID'] + 0) == 0 || ($form['L_ID'] + 0) == 0)
		$status->error($txt4);
	
	if($status->success()) {
		$infa = db_escape($form['UMOVA']);
		$status->info($txt1.$infa.$txt2);
		
		$db->query(sprintf("
			UPDATE UMOVY SET UMOVA='%s'
			WHERE U_ID=%d",
			db_escape($form['UMOVA']),
			($form['U_ID'] + 0)
		));
		
	}
	echo $status->html();
	
}
