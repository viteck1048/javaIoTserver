<!DOCTYPE html>
<html>
<head>
	<title>Lira Calc Config Editor</title>
	
	<meta http-equiv='Content-Type' content='text/html; charset=UTF-8' />
	<meta name="viewport" content="width=device-width, initial-scale=1.0">
	<link rel="stylesheet" type="text/css" href="css/default.css">
	<link rel="icon" href="css/famfam/gear.png" type="image/png">
	<script type="text/javascript" src="js/jquery-2.1.3.js"></script>
	<script type="text/javascript" src="js/application.js"></script>
	<script type="text/javascript" src="./js/edit.js"></script>
	<script type="text/javascript" src="./js/view.js"></script>
	<script type="text/javascript" src="./js/coment.js"></script>
	<script type="text/javascript" src="./js/validacija.js"></script>
	<?php
		if(isset($_GET['m_id']) && !empty($_GET['m_id'])) {
			$m_id = $_GET['m_id'] + 0;
			echo "<script type='text/javascript'>";
				echo "$(document).ready(function() {";
				echo "$('#menu-mashyn-edit-details-id".$m_id."').click();";
				echo "});";
			echo "</script>";
		}
	?>
</head>
<body>
	<!-- Бокове меню навігації -->
	<?php
		$praporec_nicjoho_ne_znajdeno = 0;
		include('config/boot.php');
		// ObrObka formy poshuku
		if(isset($_POST['search_m'])) {
			$search_m = $_POST['search_m'];
			$sql = "SELECT NAME, M_ID FROM MASHYNES WHERE NAME LIKE '%$search_m%' ORDER BY M_ID";
		} else {
			$sql = "SELECT NAME, M_ID FROM MASHYNES ORDER BY M_ID";
		}

		// Vykonyuye zaput do bazy danyh
		$res = mysqli_query($db, $sql);
		if(mysqli_num_rows($res) == 0) {
			$sql = "SELECT NAME, M_ID FROM MASHYNES ORDER BY M_ID";
			$praporec_nicjoho_ne_znajdeno = 1;
		}
		$res = mysqli_query($db, $sql);
		mysqli_close($db);
	?>
	<nav>
		<ul>
			<h1><a href='./' class='icon refresh_icon'>Машини</a></h1>
			<form action="" method="POST">
				<input type="text" name="search_m" id="search_m" placeholder="Poshuk za nazvoyu">
				<input type="submit" value="Тьрсене">
			</form>
			<?php
				$mashynes = array();
				while($m = $res->fetch_object()) {
					$mashynes[] = $m;
				}
				foreach($mashynes as $m) {
					echo 
						"<li><a href='view_liry-json.php?m_id=".$m->M_ID."&name=".$m->NAME."' class='icon view_icon mashyn-show-details' id='menu-mashyn-show-details-id".$m->M_ID."'>".$m->NAME."</a><li>";
					echo 
						"<li><a href='view_liry-json.php?m_id=".$m->M_ID."&name=".$m->NAME."' class='icon edit_icon mashyn-edit-details' id='menu-mashyn-edit-details-id".$m->M_ID."' style='display: none;'>".$m->NAME."</a><li>";
				}
			?>
			<li><a href='add_mash.php' class='icon add_icon add-mash' tabindex='-1'>Add new mashin</a></li>
		</ul>
		<?php
			$remoteAddress = $_SERVER['REMOTE_ADDR'];
			if($remoteAddress === '127.0.0.1' || $remoteAddress === '::1') {
				echo "<p><a href='phpmyadmin/index.php?route=/database/structure&db=lira_molly' class='icon user_icon mysqlviev'>преглед на БД</a>";
			}
		?>
	</nav>
	<!-- Контент сторінки -->
	<main>
		<div class='pole_opysu' id='mashyn-details'>
			<?php
			if($praporec_nicjoho_ne_znajdeno == 0) {
				echo "<h2>Vyberit' verstat zi spysku zliva</h2>";
				echo "<h2>(abo natysnit' 'add')</h2>";
				echo "<h2>((abo skorystajtes' poshukom))</h2>";
				echo "<p>tut budut' vidobrazheni jiji detali</p>";
			}
			else {
				echo
					"<p>Nichogo ne znajdeno</p>";
			}
			?>
		</div>
		<!--h1>Заголовок сторінки</h1>
		<p>Тут розміщений вміст сторінки, який може бути оформлений за зразком вікіпедії.</p>
		<p>Також можна додати таблиці, списки, зображення тощо.</p-->
		<div class='pole_instr' id='instr_coment'></div>
		<div class='pole_instr' id='instr_zvity'>
			<script>
				var instr_zvity_mem = localStorage.getItem('instr_zvity');
				if(instr_zvity_mem != null && instr_zvity_mem.length > 20000) {
					instr_zvity_mem = instr_zvity_mem.slice(-20000);
					var index = instr_zvity_mem.indexOf('<p');
					instr_zvity_mem = instr_zvity_mem.slice(index);
					localStorage.setItem('instr_zvity', instr_zvity_mem);
				}
				
				$('#instr_zvity').html(instr_zvity_mem || '');
				$('#instr_zvity').scrollTop($('#instr_zvity').prop('scrollHeight'));
			</script>
		</div>
	</main>
	
	
</body>
</html>

