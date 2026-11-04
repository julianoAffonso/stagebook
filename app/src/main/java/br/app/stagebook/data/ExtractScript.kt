package br.app.stagebook.data

/**
 * Script run on the page open in the import browser. Returns JSON with the lyrics and the title/artist hints (see
 * PageData). Order: selected text > known sites > largest text block.
 *
 * Chords: `chordText` carries the page's <pre> text with the chords on the line above (with the tablature, if any: the
 * app asks whether it comes in) and `preLyrics` the same without the marked chord names. Without a <pre>, chords are in
 * `text`. This script only gathers: what it all means (is it a chart? key, capo, BPM) is decided in the app (ChordImport).
 *
 * Letras.mus.br: on the normal page the lyrics are in .lyric-original; on the translation page (traducao.html) each
 * line carries the translation and the original in .romanization, and only the original is kept.
 */
object ExtractScript {
    val SOURCE = """
(function(){
function q(s){return document.querySelector(s);}
function t(e){return e?(e.innerText||'').trim():'';}
// a node's text with breaks: <br> = line, <p> = verse (works on nodes off screen)
function txt(n){
  if(n.nodeType===3) return n.nodeValue.replace(/\s+/g,' ');
  if(n.nodeType!==1) return '';
  var tag=n.tagName; if(tag==='BR') return '\n'; if(tag==='SCRIPT'||tag==='STYLE'||tag==='BUTTON') return '';
  var s=''; for(var i=0;i<n.childNodes.length;i++) s+=txt(n.childNodes[i]);
  if(tag==='P') return s.replace(/^ +| +$/g,'')+'\n\n';
  if(tag==='DIV'||tag==='LI'||/^H\d$/.test(tag)) return s+'\n';
  return s;
}
function lines(s){return s.split('\n').map(function(l){return l.trim();}).join('\n').replace(/\n{3,}/g,'\n\n').trim();}
var h=location.hostname,x='',ti='',ar='',ch='';
var sel=String(window.getSelection()||'').trim(); if(sel.split('\n').length>2||sel.length>80) x=sel;
var selected=!!x;
if(h.indexOf('letras.mus.br')>=0){
  if(!x){
    var orig=q('.lyric-original');
    if(orig) x=lines(txt(orig));
    else {
      var tr=q('.lyric-translation');
      if(tr && tr.querySelector('.romanization')){
        x=Array.prototype.map.call(tr.querySelectorAll('p'),function(p){
          return Array.prototype.map.call(p.querySelectorAll('.verse'),function(v){
            var r=v.querySelector('.romanization'); return t(r)||'';
          }).join('\n');
        }).join('\n\n');
        ti=t(tr.querySelector('.lyric-songName'));
      } else x=lines(txt(q('.cnt-letra')||q('.lyric')||document.createElement('div')));
    }
  }
  ti=ti||t(q('h1')); ar=t(q('h2'));
}
else if(h.indexOf('vagalume')>=0){ if(!x)x=t(q('#lyrics')); ti=t(q('h1')); ar=t(q('h2'));}
else if(h.indexOf('genius.com')>=0){ if(!x)x=Array.prototype.map.call(document.querySelectorAll('[data-lyrics-container]'),
  function(e){return e.innerText;}).join('\n'); ti=t(q('h1')); ar=t(q('a[href*="/artists/"]'));}
else if(h.indexOf('azlyrics')>=0){ if(!x){var d=document.querySelectorAll('.ringtone ~ div'); if(d.length)x=t(d[0]);}
  ti=t(q('h1')); ar=t(q('.lyricsh h2'));}
// chords above the lyrics live in <pre>, on any site, and some pages cut them into several: joined the way the page shows
// them (inline pieces continue the same line); chord names (in <b>) stay out of the lyrics
function joinPre(pre,part){return pre.map(function(e,i){var inl=i&&getComputedStyle(e).display.indexOf('inline')>=0; return (i&&!inl?'\n':'')+part(e);}).join('');}
var pre=Array.prototype.slice.call(document.querySelectorAll('pre')),pl='';
// the app decides whether it really is a chart (ChordImport.hasChords); a selection wins over the whole page
if(!selected){var all=joinPre(pre,function(e){return e.textContent;});
  if(all.length>200){ch=all; pl=joinPre(pre,function(e){var l=e.cloneNode(true); Array.prototype.forEach.call(l.querySelectorAll('b'),function(b){b.remove();}); return l.textContent;});}}
if(!x){var best='',bn=0; Array.prototype.forEach.call(document.querySelectorAll('div,pre,p,section,article'),function(e){
  if(e.querySelectorAll('div,section,article,nav,ul').length>4)return; var s=e.innerText||''; var n=s.split('\n').length;
  if(n>bn&&n<500){best=s;bn=n;}}); x=best;}
var ld={};Array.prototype.forEach.call(document.querySelectorAll('script[type="application/ld+json"]'),function(s){
  try{var j=JSON.parse(s.textContent);(Array.isArray(j)?j:[j]).forEach(function(o){(o['@graph']||[o]).forEach(function(n){
   var ty=String(n['@type']||''); if(/Music(Recording|Composition)|CreativeWork/.test(ty)){ if(!ld.name)ld.name=n.name;
   var a=n.byArtist||n.composer||n.author||n.lyricist; if(a&&!ld.artist)ld.artist=(Array.isArray(a)?a[0]:a).name||'';
   if(n.datePublished&&!ld.date)ld.date=String(n.datePublished);}});});}catch(e){}});
var og=(q('meta[property="og:title"]')||{}).content||'';
// the page's visible text: key, capo and BPM are read from it in the app (ChordImport), the same way on any site
var bt=(document.body&&document.body.innerText)||'';
return JSON.stringify({title:ti,artist:ar,text:x.slice(0,200000),chordText:ch.slice(0,200000),preLyrics:pl.slice(0,200000),pageText:bt.slice(0,50000),site:h,ldName:ld.name||'',ldArtist:ld.artist||'',ldDate:ld.date||'',
 ogTitle:og,docTitle:document.title});
})()
""".trimIndent()
}
