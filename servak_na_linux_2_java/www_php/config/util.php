<?php

function db_escape($str, $trim = true) {
	global $db;
	if($trim) {
		$str = trim($str);
	}
	return $db->real_escape_string($str);
}

class Status {
	const INFO = 0;
	const ERROR = 1;
	
	public $messages = [];
	
	public function info($msg) {
		$this->msg($msg, self::INFO);
	}
	
	public function error($msg) {
		$this->msg($msg, self::ERROR);
	}
	
	public function msg($msg, $type) {
		$this->messages[] = [
			'type'=>$type,
			'text'=>$msg
		];
	}
	
	public function success() {
		foreach($this->messages as $m) {
			if($m['type'] == self::ERROR) {
				return false;
			}
		}
		return true;
	}
	
	public function html() {
		$out = '';
		foreach($this->messages as $m) {
			date_default_timezone_set('Europe/Kyiv');
			$time = date('H:i:s');
			$css = ($m['type'] == self::INFO) ? 'msg_ok' : 'msg_err';
			$css2 = ($m['type'] == self::INFO) ? 'ok_icon' : 'error_icon';
			$out .= "<p class='icon $css2'><lable class='$css'>".$m['text']."</lable><lable style='color: #AAAAAA;'>".$time."</lable></p>";
		}
		return $out;
	}
}


if(isset($_SERVER['HTTP_ACCEPT_LANGUAGE'])){
    $lang = substr($_SERVER['HTTP_ACCEPT_LANGUAGE'], 0, 2);
    if($lang === 'uk') {
        // мова українська
		$lang = 'uk';
    }else if($lang === 'bg') {
        // мова болгарська
		$lang = 'bg';
    }else {
		// мова незнайома
		$lang = 'en';
	}
}