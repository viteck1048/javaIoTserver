<?php
include('config/boot.php');

if(isset($_POST['mash']))
{
	$form = $_POST['mash'];
	$res = $db->query(sprintf("SELECT M_ID FROM MASHYNES WHERE NAME='%s'", db_escape($form['NAME'])));
	
	if(mysqli_num_rows($res) != 0)
		$status->error("taka mashynka vzhe isnuje");
	if(strlen(trim($form['NAME'])) <= 1)
		$status->error("NAME should be more than 1 character.");
	if(strlen(trim($form['M1'])) <= 1)
		$status->error("M1 should be more than 1 character.");

	if($status->success())
		{
		
			if($form['M2'] == null) {
				$db->query(sprintf(
					"INSERT INTO MASHYNES(NAME, M1) VALUES('%s','%s')",
					db_escape($form['NAME']),
					db_escape($form['M1']),
					));
			}else {
				$db->query(sprintf(
					"INSERT INTO MASHYNES(NAME, M1, M2) VALUES('%s','%s','%s')",
					db_escape($form['NAME']),
					db_escape($form['M1']),
					db_escape($form['M2'])
					));
			}
			$res = $db->query(sprintf(
				"SELECT M_ID FROM MASHYNES WHERE NAME='%s'",
				db_escape($form['NAME'])
				));
			$m = $res->fetch_object();
			if(isset($m->M_ID) && $m->M_ID > 0) {
				$m_id = $m->M_ID;
				echo $m_id;
				exit();
			}else {
				$status->error("M1(M2) имат character.");
				echo $status->html();
				exit();
			}
		}
	echo $status->html();
	exit();
}

$mash = array();

$mash['M_ID'] = 0; 
$mash['NAME'] = '';
$mash['M1'] = '';
$mash['M2'] = '';

$form = $mash;

echo "<h1 class='icon add_icon'>новий верстат</h1>";


echo "<form id='edit-mash-form' action='add_mash.php' enctype='multipart/form-data' method='post'>";
	echo "<input type='hidden' name='mash[M_ID]' value='".htmlspecialchars($form['M_ID'])."'/>";
	echo "<p><label>NAME</label><input name='mash[NAME]' class='txt medium fc_bl_mash_name' value='".htmlspecialchars($form['NAME'])."'/></p>";
	echo "<p style='display: none;'><label>magaz_1</label><input name='mash[M1]' class='txt dovhe input_non_enter fc_bl_mash_m1' value='11 22 33 44 55 66 77 88 99 111 222'/></p>";
	echo "<p style='display: none;'><label>magaz_2</label><input name='mash[M2]' class='txt dovhe input_non_enter fc_bl_mash_m2' value=''/></p>";
	echo "<p><input type='submit' class='save-new-mash' value='Save'/></p>";
echo "</form>";

//echo "<liry 'id=liry-add'></liry>";

?>
