package com.roohu.roohux.features.browser

import android.webkit.JavascriptInterface

/**
 * The ONLY object exposed to page JavaScript (as `RoohuXBridge`). It has one method, accepts a bounded string,
 * and hands it to native code, which re-parses and re-classifies it. It can't read files, cookies or app data.
 */
class MediaBridge(private val onReport: (String) -> Unit) {
    @JavascriptInterface
    fun report(json: String?) {
        if (json.isNullOrEmpty() || json.length > MAX_REPORT_CHARS) return
        onReport(json)
    }

    companion object {
        const val NAME = "RoohuXBridge"
        private const val MAX_REPORT_CHARS = 120_000

        /**
         * Watcher injected into every page (idempotent per document). It reports <video>/<audio> sources
         * (currentSrc, src, child <source>, same-origin iframes), EME/DRM use, and recent resource URLs whenever
         * playback starts, the source changes, the DOM changes, or every 3 s as a safety net while the page is visible.
         */
        const val WATCHER_JS = """
(function(){
 var B=window.$NAME; if(!B)return;
 if(window.__rxw){window.__rxw.reset();return;}
 var timer=0,last='';
 function collect(doc,out,depth){
  try{
   doc.querySelectorAll('video,audio').forEach(function(m){
    var t=m.tagName.toLowerCase(),d=!!m.mediaKeys,cs=m.currentSrc||m.src||'';
    if(cs)out.push({u:cs,t:t,d:d});
    m.querySelectorAll('source').forEach(function(s){if(s.src)out.push({u:s.src,t:t,d:d});});
    if(!cs&&d)out.push({u:'',t:t,d:true});
   });
   if(depth<2)doc.querySelectorAll('iframe').forEach(function(f){try{if(f.contentDocument)collect(f.contentDocument,out,depth+1);}catch(e){}});
  }catch(e){}
 }
 function scan(){
  if(document.hidden)return;
  var out=[],res=[];
  collect(document,out,0);
  try{var es=performance.getEntriesByType('resource');for(var i=Math.max(0,es.length-200);i<es.length;i++)res.push(es[i].name);}catch(e){}
  var s=JSON.stringify({p:location.href,m:out,r:res});
  if(s!==last){last=s;try{B.report(s);}catch(e){}}
 }
 function sched(){clearTimeout(timer);timer=setTimeout(scan,200);}
 ['play','playing','loadstart','loadedmetadata','durationchange','canplay','emptied','encrypted'].forEach(function(e){document.addEventListener(e,sched,true);});
 try{new MutationObserver(sched).observe(document.documentElement,{subtree:true,childList:true,attributes:true,attributeFilter:['src']});}catch(e){}
 setInterval(scan,3000);
 window.__rxw={scan:scan,reset:function(){last='';sched();}};
 scan();
})();
"""
    }
}
