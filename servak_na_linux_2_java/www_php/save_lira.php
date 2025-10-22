<?php
include('config/boot.php');


if(isset($_POST['lira']))
{
	
	if($lang === 'uk') {
		$txt1 = "ліра ";
		$txt2 = " має меньше 3-х символів.";
		$txt3 = " збережена.";
		$txt5 = "індекс M_ID або L_ID дорівнює нулю.";
		$txt6 = "ім'я ліри ";
		$txt7 = "основна формула ліри ";
		$txt8 = "зворотня формула ліри ";
	}else if($lang === 'bg') {
		$txt1 = "лира ";
		$txt2 = " има по малко от 3 символа.";
		$txt3 = " сьхранена.";
		$txt5 = "индекс M_ID или L_ID е нула.";
		$txt6 = "името на лира ";
		$txt7 = "основна формула на лира ";
		$txt8 = "обратна формула на лира ";
	}else if($lang === 'en') {
		$txt1 = "the lira ";
		$txt2 = " should be more than 3 character.";
		$txt3 = " is preserved.";
		$txt5 = "index M_ID or L_ID is equal to zero.";
		$txt6 = "the name of the lira ";
		$txt7 = "the basic formula of the lira ";
		$txt8 = "he inverse formula of the lira ";
	}else {
		$txt1 = "err";
		$txt2 = "err";
		$txt3 = "err";
		$txt5 = "err";
		$txt6 = "err";
		$txt7 = "err";
		$txt8 = "err";
	}
	
	$form = $_POST['lira'];
	$infa1 = db_escape($form['NAME'] == null ? '' : $form['NAME']);
	
	if(strlen(trim($form['NAME'])) < 3)
		$status->error($txt6.$infa1.$txt2);
	if(strlen(trim($form['FORM'])) < 3)
		$status->error($txt7.$infa1.$txt2);
	if(strlen(trim($form['FORM_ZV'])) < 3)
		$status->error($txt8.$infa1.$txt2);
	if(($form['M_ID'] + 0) == 0 || ($form['L_ID'] + 0) == 0)
		$status->error($txt1.$infa1.$txt5);
	if(($form['MAGAZ'] + 0) != 1 && ($form['MAGAZ'] + 0) != 2)
		$status->error("MAGAZ != 1/2");
	if(($form['BR_KOL_LIR'] + 0) < 2 || ($form['BR_KOL_LIR'] + 0) > 4)
		$status->error("BR_KOL_LIR != 2...4");
	
	if($status->success())
		{
			$result = $db->query(sprintf(
				"UPDATE LIRY SET NAME='%s', FORM='%s', FORM_ZV='%s', MAGAZ=%d, BR_KOL_LIR=%d
				WHERE L_ID=%d",
				db_escape($form['NAME']),
				db_escape($form['FORM']),
				db_escape($form['FORM_ZV']),
				($form['MAGAZ'] + 0),
				($form['BR_KOL_LIR'] + 0),
				($form['L_ID'] + 0)
			));
			if ($result === false) {
				$error = $db->error;
				$status->error($error);
			} else {
				$status->info($txt1.$infa1.$txt3);
			}
		}
	echo $status->html();
}
