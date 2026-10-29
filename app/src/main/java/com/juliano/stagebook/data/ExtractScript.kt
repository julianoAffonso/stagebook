package com.juliano.stagebook.data

/**
 * Script rodado na página aberta no navegador de importação. Devolve um JSON com a letra e as pistas
 * de título/artista (ver PageData). Ordem: texto selecionado > sites conhecidos > maior bloco de texto.
 *
 * Cifra (Cifra Club): `chordText` traz o bloco com os acordes na linha de cima (sem tablatura), `key` e `capo` o
 * que a página diz; `text` continua só a letra. Em outros sites a cifra, se houver, está no próprio `text`.
 *
 * Letras.mus.br: na página normal a letra está em .lyric-original; na de tradução (traducao.html)
 * cada verso traz a tradução e o original em .romanization — fica só o original.
 */
object ExtractScript {
    val SOURCE = """
(function(){
function q(s){return document.querySelector(s);}
function t(e){return e?(e.innerText||'').trim():'';}
// texto de um nó com quebras: <br> = linha, <p> = estrofe (funciona em nós fora da tela)
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
var h=location.hostname,x='',ti='',ar='',ch='',ky='',cp='';
var sel=String(window.getSelection()||'').trim(); if(sel.split('\n').length>2||sel.length>80) x=sel;
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
else if(h.indexOf('cifraclub')>=0){ var p=q('pre'); if(p){var c=p.cloneNode(true);
  Array.prototype.forEach.call(c.querySelectorAll('.tablatura,.tab'),function(e){e.remove();}); ch=c.textContent;
  var l=c.cloneNode(true); Array.prototype.forEach.call(l.querySelectorAll('b'),function(e){e.remove();}); if(!x)x=l.textContent;}
  ti=t(q('h1')); ar=t(q('h2')); ky=t(q('#cifra_tom')); cp=t(q('#cifra_capo'));}
else if(h.indexOf('azlyrics')>=0){ if(!x){var d=document.querySelectorAll('.ringtone ~ div'); if(d.length)x=t(d[0]);}
  ti=t(q('h1')); ar=t(q('.lyricsh h2'));}
if(!x){var best='',bn=0; Array.prototype.forEach.call(document.querySelectorAll('div,pre,p,section,article'),function(e){
  if(e.querySelectorAll('div,section,article,nav,ul').length>4)return; var s=e.innerText||''; var n=s.split('\n').length;
  if(n>bn&&n<500){best=s;bn=n;}}); x=best;}
var ld={};Array.prototype.forEach.call(document.querySelectorAll('script[type="application/ld+json"]'),function(s){
  try{var j=JSON.parse(s.textContent);(Array.isArray(j)?j:[j]).forEach(function(o){(o['@graph']||[o]).forEach(function(n){
   var ty=String(n['@type']||''); if(/Music(Recording|Composition)|CreativeWork/.test(ty)){ if(!ld.name)ld.name=n.name;
   var a=n.byArtist||n.composer||n.author||n.lyricist; if(a&&!ld.artist)ld.artist=(Array.isArray(a)?a[0]:a).name||'';
   if(n.datePublished&&!ld.date)ld.date=String(n.datePublished);}});});}catch(e){}});
var og=(q('meta[property="og:title"]')||{}).content||'';
// tom e capotraste escritos na página (Cifra Club e parecidos), se o site não os deu acima
var bt=(document.body&&document.body.innerText)||''; var km=bt.match(/Tom:\s*([A-G][#b]?m?)/); if(!ky&&km)ky=km[1];
var cm=bt.match(/Capotraste na \d+/i); if(!cp&&cm)cp=cm[0];
// BPM escrito na página (qualquer site): "120 BPM", "BPM: 120", "Tempo: 120"
var bm=bt.match(/(\d{2,3})\s*bpm\b/i)||bt.match(/\b(?:bpm|tempo)\s*[:=]?\s*(\d{2,3})\b/i); var bp=bm?bm[1]:'';
return JSON.stringify({title:ti,artist:ar,text:x,chordText:ch,key:ky,capo:cp,bpm:bp,site:h,ldName:ld.name||'',ldArtist:ld.artist||'',ldDate:ld.date||'',
 ogTitle:og,docTitle:document.title});
})()
""".trimIndent()
}
