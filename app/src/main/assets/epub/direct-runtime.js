(function(){
  var TOKEN=__LEGADO_TOKEN__;
  var initialTextImageMode=typeof __LEGADO_TEXT_IMAGE_MODE__==='undefined'?'epub':String(__LEGADO_TEXT_IMAGE_MODE__);
  if(window.__legadoEpub){
    if(window.__legadoEpub.setTextImageMode)window.__legadoEpub.setTextImageMode(initialTextImageMode);
    window.__legadoEpub.setToken(TOKEN);window.__legadoEpub.report();
    requestAnimationFrame(function(){
      var api=window.__legadoEpub;
      var bridge=window.__LEGADO_BRIDGE__;
      if(api&&api.token===TOKEN&&typeof api.replayRuntimeTerminal==='function'){
        api.replayRuntimeTerminal(TOKEN);
      }else if(api&&api.token===TOKEN&&api.metrics().ready&&bridge&&typeof bridge.onStable==='function'){
        bridge.onStable(TOKEN);
      }
    });
    return;
  }
  var activeToken=TOKEN;
  var runtimeActive=true;
  var textImageMode=initialTextImageMode;
  var vertical=__LEGADO_VERTICAL__;
  var fixed=__LEGADO_FIXED__;
  var scaled=__LEGADO_SCALED__;
  var publisherStyled=__LEGADO_PUBLISHER_STYLED__;
  var readerTypographyEnabled=typeof __LEGADO_READER_TYPOGRAPHY__==='undefined'
    ?!fixed&&!publisherStyled:__LEGADO_READER_TYPOGRAPHY__;
  var readerBottomJustify=typeof __LEGADO_BOTTOM_JUSTIFY__==='undefined'||__LEGADO_BOTTOM_JUSTIFY__;
  var readerLayoutMutationObserver=null;
  var readerLayoutMutationOptions={subtree:true,childList:true,characterData:true,attributes:true,
    attributeOldValue:true,attributeFilter:['class','style','hidden','src','srcset','href','poster','data','width','height','sizes']};
  var publisherFullscreen=__LEGADO_PUBLISHER_FULLSCREEN__;
  var readerSafeInsetLeftPx=__LEGADO_SAFE_INSET_LEFT__;
  var readerSafeInsetTopPx=__LEGADO_SAFE_INSET_TOP__;
  var readerSafeInsetRightPx=__LEGADO_SAFE_INSET_RIGHT__;
  var readerSafeInsetBottomPx=__LEGADO_SAFE_INSET_BOTTOM__;
  var useReaderSafeInsets=!fixed&&!publisherStyled;
  var readerPaddingLeftPx=__LEGADO_PADDING_LEFT__+(useReaderSafeInsets?readerSafeInsetLeftPx:0);
  var readerPaddingTopPx=__LEGADO_PADDING_TOP__+(useReaderSafeInsets?readerSafeInsetTopPx:0);
  var readerPaddingRightPx=__LEGADO_PADDING_RIGHT__+(useReaderSafeInsets?readerSafeInsetRightPx:0);
  var readerPaddingBottomPx=__LEGADO_PADDING_BOTTOM__+(useReaderSafeInsets?readerSafeInsetBottomPx:0);
  var rtl=__LEGADO_RTL__;
  var startId=__LEGADO_START_ID__;
  var endId=__LEGADO_END_ID__;
  var annotationTitleLabel=typeof __LEGADO_ANNOTATION_TITLE__==='undefined'?'Note':__LEGADO_ANNOTATION_TITLE__;
  var annotationCloseLabel=typeof __LEGADO_ANNOTATION_CLOSE__==='undefined'?'Close':__LEGADO_ANNOTATION_CLOSE__;
  var annotationBackLabel=typeof __LEGADO_ANNOTATION_BACK__==='undefined'?'Back':__LEGADO_ANNOTATION_BACK__;
  var readerFontConfigured=typeof __LEGADO_READER_FONT__==='undefined'?false:__LEGADO_READER_FONT__;
  // Reader chrome is rendered inside the document as a page-local absolute
  // frame.  The placeholders deliberately have safe defaults so older native
  // callers can keep using the same runtime asset while the feature is off.
  var readerChromeEnabled=typeof __LEGADO_CHROME_ENABLED__==='undefined'?false:__LEGADO_CHROME_ENABLED__;
  var readerChromeHeaderEnabled=typeof __LEGADO_CHROME_HEADER_ENABLED__==='undefined'?false:__LEGADO_CHROME_HEADER_ENABLED__;
  var readerChromeFooterEnabled=typeof __LEGADO_CHROME_FOOTER_ENABLED__==='undefined'?false:__LEGADO_CHROME_FOOTER_ENABLED__;
  var readerChromeHideHeaderOnFirstPage=typeof __LEGADO_CHROME_HIDE_HEADER_FIRST__==='undefined'?true:__LEGADO_CHROME_HIDE_HEADER_FIRST__;
  var readerChromeHeaderHeightPx=typeof __LEGADO_CHROME_HEADER_HEIGHT__==='undefined'?0:Number(__LEGADO_CHROME_HEADER_HEIGHT__)||0;
  var readerChromeFooterHeightPx=typeof __LEGADO_CHROME_FOOTER_HEIGHT__==='undefined'?0:Number(__LEGADO_CHROME_FOOTER_HEIGHT__)||0;
  var readerChromeHeaderPaddingLeftPx=typeof __LEGADO_CHROME_HEADER_PADDING_LEFT__==='undefined'?0:Number(__LEGADO_CHROME_HEADER_PADDING_LEFT__)||0;
  var readerChromeHeaderPaddingTopPx=typeof __LEGADO_CHROME_HEADER_PADDING_TOP__==='undefined'?0:Number(__LEGADO_CHROME_HEADER_PADDING_TOP__)||0;
  var readerChromeHeaderPaddingRightPx=typeof __LEGADO_CHROME_HEADER_PADDING_RIGHT__==='undefined'?0:Number(__LEGADO_CHROME_HEADER_PADDING_RIGHT__)||0;
  var readerChromeHeaderPaddingBottomPx=typeof __LEGADO_CHROME_HEADER_PADDING_BOTTOM__==='undefined'?0:Number(__LEGADO_CHROME_HEADER_PADDING_BOTTOM__)||0;
  var readerChromeFooterPaddingLeftPx=typeof __LEGADO_CHROME_FOOTER_PADDING_LEFT__==='undefined'?0:Number(__LEGADO_CHROME_FOOTER_PADDING_LEFT__)||0;
  var readerChromeFooterPaddingTopPx=typeof __LEGADO_CHROME_FOOTER_PADDING_TOP__==='undefined'?0:Number(__LEGADO_CHROME_FOOTER_PADDING_TOP__)||0;
  var readerChromeFooterPaddingRightPx=typeof __LEGADO_CHROME_FOOTER_PADDING_RIGHT__==='undefined'?0:Number(__LEGADO_CHROME_FOOTER_PADDING_RIGHT__)||0;
  var readerChromeFooterPaddingBottomPx=typeof __LEGADO_CHROME_FOOTER_PADDING_BOTTOM__==='undefined'?0:Number(__LEGADO_CHROME_FOOTER_PADDING_BOTTOM__)||0;
  var readerChromeTextSizePx=typeof __LEGADO_CHROME_TEXT_SIZE__==='undefined'?12:Number(__LEGADO_CHROME_TEXT_SIZE__)||12;
  var readerChromeTextColor=typeof __LEGADO_CHROME_TEXT_COLOR__==='undefined'?0:Number(__LEGADO_CHROME_TEXT_COLOR__)||0;
  var readerChromeDividerColor=typeof __LEGADO_CHROME_DIVIDER_COLOR__==='undefined'?0:Number(__LEGADO_CHROME_DIVIDER_COLOR__)||0;
  var readerChromeHeaderDividerEnabled=typeof __LEGADO_CHROME_HEADER_DIVIDER__==='undefined'?false:__LEGADO_CHROME_HEADER_DIVIDER__;
  var readerChromeFooterDividerEnabled=typeof __LEGADO_CHROME_FOOTER_DIVIDER__==='undefined'?false:__LEGADO_CHROME_FOOTER_DIVIDER__;
  var readerChromeLayer=null;
  var readerChromeHeader=null;
  var readerChromeFooter=null;
  var readerChromeHeaderSpans=null;
  var readerChromeFooterSpans=null;
  var readerChromeData={};
  var readerChromeConfigKey='';
  var readerChromeScale=Math.max(1,Number(window.devicePixelRatio)||1);

  function readerChromeCssPx(value){return Math.max(0,Number(value)||0)/readerChromeScale;}

  function readerChromeColor(value,alphaMultiplier){
    var color=Number(value)||0;
    var alpha=((color>>>24)&255)/255;
    if(alphaMultiplier!=null)alpha*=Math.max(0,Math.min(1,Number(alphaMultiplier)||0));
    return 'rgba('+((color>>>16)&255)+','+((color>>>8)&255)+','+(color&255)+','+alpha.toFixed(3)+')';
  }

  function isReaderChromeNode(node){
    var element=node&&node.nodeType===Node.ELEMENT_NODE?node:node&&node.parentElement;
    if(!element)return false;
    if(readerChromeLayer&&(element===readerChromeLayer||readerChromeLayer.contains(element)))return true;
    try{return !!(element.closest&&element.closest('[data-legado-runtime="reader-chrome"]'));}catch(_){return false;}
  }

  function isReaderChromeMutation(mutation){
    if(!mutation)return false;
    if(isReaderChromeNode(mutation.target))return true;
    if(mutation.type!=='childList')return false;
    var nodes=Array.prototype.slice.call(mutation.addedNodes||[]).concat(
      Array.prototype.slice.call(mutation.removedNodes||[])
    );
    return nodes.length>0&&nodes.every(isReaderChromeNode);
  }

  function readerChromeSupported(){
    return !!(readerChromeEnabled&&root&&!vertical&&!fixed&&!publisherStyled&&
      window.innerWidth>0&&window.innerHeight>0);
  }

  function readerChromeSlot(kind,height){
    var slot=document.createElement('div');
    var header=kind==='header';
    var paddingLeft=(header?readerChromeHeaderPaddingLeftPx:readerChromeFooterPaddingLeftPx)+readerSafeInsetLeftPx;
    var paddingTop=(header?readerChromeHeaderPaddingTopPx:readerChromeFooterPaddingTopPx)+(header?readerSafeInsetTopPx:0);
    var paddingRight=(header?readerChromeHeaderPaddingRightPx:readerChromeFooterPaddingRightPx)+readerSafeInsetRightPx;
    var paddingBottom=(header?readerChromeHeaderPaddingBottomPx:readerChromeFooterPaddingBottomPx)+(!header?readerSafeInsetBottomPx:0);
    var safeHeight=height+(header?readerSafeInsetTopPx:readerSafeInsetBottomPx);
    slot.className='legado-epub-reader-chrome-'+kind;
    slot.setAttribute('data-legado-runtime','reader-chrome');
    slot.setAttribute('data-legado-reader-chrome-slot',kind);
    slot.style.cssText='position:absolute!important;box-sizing:border-box!important;display:grid!important;'+
      'grid-template-columns:minmax(0,1fr) auto minmax(0,1fr)!important;align-items:center!important;'+
      'gap:8px!important;overflow:hidden!important;pointer-events:none!important;user-select:none!important;'+
      '-webkit-user-select:none!important;height:'+readerChromeCssPx(safeHeight)+'px!important;'+
      'padding:'+readerChromeCssPx(paddingTop)+'px '+readerChromeCssPx(paddingRight)+'px '+
      readerChromeCssPx(paddingBottom)+'px '+readerChromeCssPx(paddingLeft)+'px!important;'+
      'font-size:'+Math.max(1,readerChromeCssPx(readerChromeTextSizePx))+'px!important;line-height:1.2!important;'+
      'color:'+readerChromeColor(readerChromeTextColor)+'!important;background:transparent!important;'+
      'border:0!important;box-shadow:none!important;z-index:2!important;';
    var spans=[];
    ['left','center','right'].forEach(function(position){
      var span=document.createElement('span');
      span.className='legado-epub-reader-chrome-'+position;
      span.setAttribute('data-legado-runtime','reader-chrome');
      span.style.cssText='display:block!important;min-width:0!important;overflow:hidden!important;'+
        'text-overflow:ellipsis!important;white-space:nowrap!important;pointer-events:none!important;'+
        'user-select:none!important;-webkit-user-select:none!important;background:transparent!important;'+
        'border:0!important;box-shadow:none!important;text-align:'+position+'!important;';
      slot.appendChild(span);spans.push(span);
    });
    if(kind==='header')readerChromeHeaderSpans=spans;else readerChromeFooterSpans=spans;
    return slot;
  }

  function ensureReaderChrome(){
    if(!readerChromeSupported())return null;
    if(readerChromeLayer&&root.contains(readerChromeLayer))return readerChromeLayer;
    readerChromeLayer=document.createElement('div');
    readerChromeLayer.id='legado-epub-reader-chrome';
    readerChromeLayer.setAttribute('data-legado-runtime','reader-chrome');
    readerChromeLayer.setAttribute('aria-hidden','true');
    readerChromeLayer.style.cssText='position:absolute!important;left:0!important;top:0!important;'+
      'width:100%!important;height:100%!important;margin:0!important;padding:0!important;'+
      'overflow:visible!important;pointer-events:none!important;user-select:none!important;'+
      '-webkit-user-select:none!important;background:transparent!important;border:0!important;'+
      'box-shadow:none!important;z-index:2!important;';
    if(readerChromeHeaderEnabled&&readerChromeHeaderHeightPx>0){
      readerChromeHeader=readerChromeSlot('header',readerChromeHeaderHeightPx);
      readerChromeLayer.appendChild(readerChromeHeader);
    }
    if(readerChromeFooterEnabled&&readerChromeFooterHeightPx>0){
      readerChromeFooter=readerChromeSlot('footer',readerChromeFooterHeightPx);
      readerChromeLayer.appendChild(readerChromeFooter);
    }
    root.appendChild(readerChromeLayer);
    applyReaderChromeData();
    return readerChromeLayer;
  }

  function removeReaderChrome(){
    if(readerChromeLayer&&readerChromeLayer.parentNode)readerChromeLayer.parentNode.removeChild(readerChromeLayer);
    readerChromeLayer=null;readerChromeHeader=null;readerChromeFooter=null;
    readerChromeHeaderSpans=null;readerChromeFooterSpans=null;
  }

  function applyReaderChromeData(){
    var data=readerChromeData||{};
    var header=[data.headerLeft,data.headerCenter,data.headerRight];
    var footer=[data.footerLeft,data.footerCenter,data.footerRight];
    if(readerChromeHeaderSpans)readerChromeHeaderSpans.forEach(function(span,index){span.textContent=String(header[index]||'');});
    if(readerChromeFooterSpans)readerChromeFooterSpans.forEach(function(span,index){span.textContent=String(footer[index]||'');});
    if(readerChromeHeader)readerChromeHeader.style.setProperty('border-bottom',readerChromeHeaderDividerEnabled?
      '1px solid '+readerChromeColor(readerChromeDividerColor):'0 solid transparent','important');
    if(readerChromeFooter)readerChromeFooter.style.setProperty('border-top',readerChromeFooterDividerEnabled?
      '1px solid '+readerChromeColor(readerChromeDividerColor):'0 solid transparent','important');
    updateReaderChromeVisibility();
  }

  function updateReaderChromeVisibility(){
    if(readerChromeMotionPending)return;
    var first=readerChromeData&&readerChromeData.chapterFirstPage===true||currentPage===0;
    if(readerChromeHeader)readerChromeHeader.style.setProperty('visibility',
      readerChromeHideHeaderOnFirstPage&&first?'hidden':'visible','important');
    if(readerChromeFooter)readerChromeFooter.style.setProperty('visibility','visible','important');
  }

  function positionReaderChrome(){
    if(!readerChromeSupported()||readerChromeMotionPending)return;
    var layer=ensureReaderChrome();
    if(!layer)return;
    var extent=Math.max(1,window.innerWidth);
    var page=Math.max(0,windowStartPage+Math.max(0,Number(currentPage)||0));
    var pageLeft=layoutRtl?-page*extent:page*extent;
    layer.style.setProperty('left',String(pageLeft)+'px','important');
    layer.style.setProperty('right','auto','important');
    layer.style.setProperty('top','0px','important');
    layer.style.setProperty('width',String(extent)+'px','important');
    layer.style.setProperty('height',String(Math.max(1,window.innerHeight))+'px','important');
    if(readerChromeHeader){
      readerChromeHeader.style.setProperty('left','0px','important');
      readerChromeHeader.style.setProperty('right','0px','important');
      readerChromeHeader.style.setProperty('width','auto','important');
      readerChromeHeader.style.setProperty('top','0px','important');
    }
    if(readerChromeFooter){
      readerChromeFooter.style.setProperty('left','0px','important');
      readerChromeFooter.style.setProperty('right','0px','important');
      readerChromeFooter.style.setProperty('width','auto','important');
      readerChromeFooter.style.setProperty('top',String(Math.max(0,window.innerHeight-readerChromeCssPx(readerChromeFooterHeightPx+readerSafeInsetBottomPx)))+'px','important');
    }
    updateReaderChromeVisibility();
  }

  function updateReaderChromeForDisplayedPage(){
    if(!readerChromeSupported()||readerChromeMotionPending)return;
    var count=Math.max(1,Number(cachedPageCount)||1);
    var displayed=Math.max(0,Math.min(count-1,displayedPageIndex()));
    if(displayed!==currentPage)currentPage=displayed;
    positionReaderChrome();
  }

  function readerChromeConfigSignature(){
    return [
      readerChromeEnabled,readerChromeHeaderEnabled,readerChromeFooterEnabled,
      readerChromeHideHeaderOnFirstPage,readerChromeHeaderHeightPx,readerChromeFooterHeightPx,
      readerChromeHeaderPaddingLeftPx,readerChromeHeaderPaddingTopPx,
      readerChromeHeaderPaddingRightPx,readerChromeHeaderPaddingBottomPx,
      readerChromeFooterPaddingLeftPx,readerChromeFooterPaddingTopPx,
      readerChromeFooterPaddingRightPx,readerChromeFooterPaddingBottomPx,
      readerChromeTextSizePx,readerChromeTextColor,readerChromeDividerColor,
      readerChromeHeaderDividerEnabled,readerChromeFooterDividerEnabled
    ].join('|');
  }

  function setReaderChrome(config){
    config=config&&typeof config==='object'?config:{};
    readerChromeEnabled=!!config.enabled;
    readerChromeHeaderEnabled=!!config.headerEnabled;
    readerChromeFooterEnabled=!!config.footerEnabled;
    readerChromeHideHeaderOnFirstPage=config.hideHeaderOnChapterFirstPage!==false;
    readerChromeHeaderHeightPx=Math.max(0,Number(config.headerHeightPx)||0);
    readerChromeFooterHeightPx=Math.max(0,Number(config.footerHeightPx)||0);
    readerChromeHeaderPaddingLeftPx=Math.max(0,Number(config.headerPaddingLeftPx)||0);
    readerChromeHeaderPaddingTopPx=Math.max(0,Number(config.headerPaddingTopPx)||0);
    readerChromeHeaderPaddingRightPx=Math.max(0,Number(config.headerPaddingRightPx)||0);
    readerChromeHeaderPaddingBottomPx=Math.max(0,Number(config.headerPaddingBottomPx)||0);
    readerChromeFooterPaddingLeftPx=Math.max(0,Number(config.footerPaddingLeftPx)||0);
    readerChromeFooterPaddingTopPx=Math.max(0,Number(config.footerPaddingTopPx)||0);
    readerChromeFooterPaddingRightPx=Math.max(0,Number(config.footerPaddingRightPx)||0);
    readerChromeFooterPaddingBottomPx=Math.max(0,Number(config.footerPaddingBottomPx)||0);
    readerChromeTextSizePx=Math.max(1,Number(config.textSizePx)||12);
    readerChromeTextColor=Number(config.textColor)||0;
    readerChromeDividerColor=Number(config.dividerColor)||0;
    readerChromeHeaderDividerEnabled=!!config.headerDividerEnabled;
    readerChromeFooterDividerEnabled=!!config.footerDividerEnabled;
    var nextKey=readerChromeConfigSignature();
    if(nextKey===readerChromeConfigKey){
      applyReaderChromeData();
      positionReaderChrome();
      return;
    }
    readerChromeConfigKey=nextKey;
    removeReaderChrome();
    if(readerChromeSupported())positionReaderChrome();
  }

  function setReaderChromeData(data){
    readerChromeData=data&&typeof data==='object'?data:{};
    applyReaderChromeData();
    positionReaderChrome();
  }
  var currentPage=0;
  var timer=0;
  var horizontalScrollAnimationFrame=0;
  var readerChromeMotionPending=false;
  var stable=false;
  var resourcesReady=false;
  var resourcesFailed=false;
  var runtimeTerminal=null;
  var runtimeTerminalReportedToken=null;
  // A live DOM range owns the viewport.  Reflow/layout callbacks may continue to run while
  // the user is dragging selection handles, but they must not replace the selected page.
  var selectionPageLock=false;
  var layoutRefreshDeferredBySelection=false;
  var root=document.body||document.documentElement;
  function hasBackgroundArtwork(node){
    if(!node)return false;
    try{return String(window.getComputedStyle(node).backgroundImage||'none').toLowerCase()!=='none';}
    catch(_){return false;}
  }
  function hasComplexParagraphLayout(paragraph){
    if(paragraph.querySelector('svg,video,canvas,object,embed,iframe,table,math'))return true;
    if(Array.prototype.some.call(paragraph.querySelectorAll('img'),function(image){
      return !image.matches('.legado-text-inline-image,.legado-text-bubble')&&!image.closest('.legado-text-image-frame');
    }))return true;
    var node=paragraph;
    while(node&&node!==root){
      try{
        var style=window.getComputedStyle(node);
        var display=String(style.display||'').toLowerCase();
        var position=String(style.position||'').toLowerCase();
        var writingMode=String(style.writingMode||style.webkitWritingMode||'').toLowerCase();
        if(hasBackgroundArtwork(node)||/^(?:absolute|fixed|sticky)$/.test(position)||
          /^(?:inline-)?(?:flex|grid)$/.test(display)||display.indexOf('table')===0||
          String(style.cssFloat||style.float||'none').toLowerCase()!=='none'||
          String(style.transform||'none').toLowerCase()!=='none'||
          /^(?:vertical|sideways)-/.test(writingMode))return true;
      }catch(_){ }
      node=node.parentElement;
    }
    return false;
  }
  function markReaderParagraphs(){
    if(!root||!root.querySelectorAll)return;
    Array.prototype.forEach.call(root.querySelectorAll('p[data-legado-reader-paragraph]'),function(paragraph){
      paragraph.removeAttribute('data-legado-reader-paragraph');
    });
    if(!readerTypographyEnabled||hasBackgroundArtwork(document.documentElement)||hasBackgroundArtwork(root))return;
    var blockedTags='aside,nav,blockquote,li,figure,figcaption,table,pre,code,header,footer,address,details,summary';
    var blockedSemantics=/(?:^|[\s_-])(title|subtitle|caption|footnote|endnote|annotation|note|poem|poetry|verse|stanza|lyrics|epigraph|dedication|copyright|imprint|credits|colophon|toc|index|bibliography)(?:$|[\s_-])/i;
    var marked=[];
    Array.prototype.forEach.call(root.querySelectorAll('p'),function(paragraph){
      if(!String(paragraph.textContent||'').trim()||paragraph.closest(blockedTags))return;
      var node=paragraph;
      while(node&&node!==root.parentElement){
        var semantic=[node.id,node.className,node.getAttribute&&node.getAttribute('role'),
          node.getAttribute&&node.getAttribute('epub:type')].join(' ');
        if(blockedSemantics.test(semantic))return;
        if(node===root)break;
        node=node.parentElement;
      }
      if(hasComplexParagraphLayout(paragraph))return;
      marked.push(paragraph);
    });
    // Attribute writes change the typography selector. Batch them after all
    // style reads to avoid a full-column layout for every paragraph in a book.
    marked.forEach(function(paragraph){paragraph.setAttribute('data-legado-reader-paragraph','true');});
  }
  var readerLineGridValue='';
  var readerColumnFit=null;
  function clearReaderColumnFit(){
    if(!readerColumnFit||!root)return;
    var saved=readerColumnFit;readerColumnFit=null;
    // An author may have replaced the inline padding while the page was open.
    // Restore only the value still owned by this layout pass.
    saved.styles.forEach(function(property){
      if(root.style.getPropertyValue(property.name)!==property.applied||
        root.style.getPropertyPriority(property.name)!=='important')return;
      if(property.value)root.style.setProperty(property.name,property.value,property.priority);
      else root.style.removeProperty(property.name);
    });
  }
  function clearReaderPageSpacing(){
    if(!root)return;
    Array.prototype.forEach.call(root.querySelectorAll('[data-legado-page-gap]'),function(node){
      node.style.removeProperty('--legado-page-gap');
      node.removeAttribute('data-legado-page-gap');
    });
    Array.prototype.forEach.call(root.querySelectorAll('[data-legado-highlight-grid]'),function(node){
      node.style.removeProperty('--legado-highlight-line-grid');
      node.removeAttribute('data-legado-highlight-grid');
    });
  }
  function clearReaderLineGrid(){
    if(!readerLineGridValue||!root)return;
    readerLineGridValue='';
    root.style.removeProperty('--legado-line-grid');
  }
  function alignReaderLineGrid(){
    clearReaderColumnFit();
    clearReaderPageSpacing();
    clearReaderLineGrid();
    if(!readerBottomJustify||!readerTypographyEnabled||vertical||fixed||publisherStyled||!root){clearReaderLineGrid();return;}
    var paragraphs=Array.prototype.slice.call(root.querySelectorAll('p[data-legado-reader-paragraph]'));
    if(!paragraphs.length){clearReaderLineGrid();return;}
    if(hasBackgroundArtwork(root)||hasBackgroundArtwork(document.documentElement)){clearReaderLineGrid();return;}
    var bodyStyle;
    try{bodyStyle=window.getComputedStyle(root);}catch(_){clearReaderLineGrid();return;}
    var line=Number.parseFloat(bodyStyle.getPropertyValue('--legado-line-height-base'));
    if(!isFinite(line)||line<=0){clearReaderLineGrid();return;}
    for(var i=0;i<paragraphs.length;i++){
      try{
        var style=window.getComputedStyle(paragraphs[i]);
        if(style.display!=='block'||style.position!=='static'||style.float!=='none'||
          style.transform!=='none'||style.backgroundImage!=='none'){clearReaderLineGrid();return;}
      }catch(_){clearReaderLineGrid();return;}
    }
    var height=Math.max(1,root.clientHeight||window.innerHeight);
    var paddingTop=Number.parseFloat(bodyStyle.paddingTop)||0;
    var paddingBottom=Number.parseFloat(bodyStyle.paddingBottom)||0;
    var available=Math.max(1,height-paddingTop-paddingBottom);
    function fittedLineHeight(base,canTighten){
      var lines=canTighten?Math.round(available/base):Math.floor(available/base);
      if(lines<2)return '';
      // Chromium lays out in 1/64 CSS pixels. Decimal rounding upward can
      // overflow a nominally exact column and throw a whole line onto the next
      // page. Round down to the layout unit, retaining exact integer heights.
      var adjusted=Math.floor(available/lines*64)/64;
      var maxDelta=Math.min(2,Math.max(.5,base*.06));
      var minimum=canTighten?Math.max(base-maxDelta,(Number.parseFloat(bodyStyle.fontSize)||0)*1.5):base-1/64;
      return adjusted>=minimum&&Math.abs(adjusted-base)<=maxDelta?adjusted+'px':'';
    }
    var value=fittedLineHeight(line,true);
    if(value){
      readerLineGridValue=value;
      root.style.setProperty('--legado-line-grid',value,'important');
    }
    var fittedFlows=[];
    paragraphs.forEach(function(paragraph){
      Array.prototype.forEach.call(paragraph.querySelectorAll('[data-legado-highlight-flow]'),function(flow){
        var base=Number.parseFloat(window.getComputedStyle(flow).lineHeight)||line;
        Array.prototype.forEach.call(flow.querySelectorAll('[data-legado-highlight]'),function(highlight){
          base=Math.max(base,Number.parseFloat(window.getComputedStyle(highlight).lineHeight)||base);
        });
        var fitted=fittedLineHeight(base,false);
        if(fitted)fittedFlows.push({node:flow,value:fitted});
      });
    });
    fittedFlows.forEach(function(flow){
      flow.node.setAttribute('data-legado-highlight-grid','true');
      flow.node.style.setProperty('--legado-highlight-line-grid',flow.value,'important');
    });
  }

  function fitReaderColumnContent(style,extra){
    // Keep publisher media/poetry/table layouts on their original break rules.
    // Headings retain their occupied space; the final containment check covers
    // them as well as paragraphs if a changed break lands beside a heading.
    if(Array.prototype.some.call(root.querySelectorAll('figure,picture,table,pre,blockquote,svg,video,canvas,iframe,object,embed,math,ruby'),function(node){
        return !node.closest('[data-legado-reader-chrome]');
      })||
      Array.prototype.some.call(root.querySelectorAll('img'),function(image){
        return !image.closest('[data-legado-reader-chrome]')&&
          !image.matches('.legado-text-inline-image,.legado-text-bubble')&&!image.closest('.legado-text-image-frame');
      }))return;
    var padding=Number.parseFloat(style.paddingBottom)||0;
    var leading=window.LegadoPageAlignment.leading(root,'p[data-legado-reader-paragraph]');
    if(leading<.5)return false;
    extra=extra||0;
    leading=Math.floor((extra?leading:Math.min(padding,leading))*64)/64;
    var borrowed=Math.floor((leading+extra)*64)/64;
    if(borrowed<.5||extra&&style.boxSizing!=='border-box')return false;
    var height=root.getBoundingClientRect().height;
    var saved={leading:leading,extra:extra,styles:[]};
    function apply(name,value){
      var property={name:name,value:root.style.getPropertyValue(name),priority:root.style.getPropertyPriority(name)};
      root.style.setProperty(name,value,'important');property.applied=root.style.getPropertyValue(name);
      saved.styles.push(property);
    }
    apply('padding-bottom',Math.max(0,padding-borrowed)+'px');
    // The CSS fragmentainer may briefly be taller than the painted page. Whole
    // lines are accepted only if the shared fitter can place their glyphs and
    // artwork inside the original bounds with a small spacing correction.
    if(borrowed>padding)apply('height',(height+borrowed-padding)+'px');
    readerColumnFit=saved;
    markLayoutDirty();
    return true;
  }
  // Fit complete lines first, then distribute only the remaining page surplus.
  function alignReaderPageGaps(){
    if(!readerBottomJustify||!readerTypographyEnabled||vertical||fixed||publisherStyled||layoutRtl||
      !root||!window.LegadoPageAlignment)return;
    var style=window.getComputedStyle(root),rect=root.getBoundingClientRect();
    var bounds={left:rect.left+(Number.parseFloat(style.paddingLeft)||0),
      top:rect.top+(Number.parseFloat(style.borderTopWidth)||0)+(Number.parseFloat(style.paddingTop)||0),
      bottom:rect.bottom-(Number.parseFloat(style.borderBottomWidth)||0)-(Number.parseFloat(style.paddingBottom)||0)};
    var paintBounds={top:Math.max(0,rect.top),bottom:Math.min(window.innerHeight,rect.bottom)};
    Array.prototype.forEach.call(root.querySelectorAll('[data-legado-reader-chrome]'),function(chrome){
      var walker=document.createTreeWalker(chrome,NodeFilter.SHOW_TEXT),node;
      function avoid(box){
        if(box.width<=.1||box.height<=.1)return;
        var center=(box.top+box.bottom)/2;
        if(center<=bounds.top+.1)paintBounds.top=Math.max(paintBounds.top,box.bottom);
        if(center>=bounds.bottom-.1)paintBounds.bottom=Math.min(paintBounds.bottom,box.top);
      }
      while((node=walker.nextNode()))if(node.data.trim()){
        var range=document.createRange();range.selectNodeContents(node);
        Array.prototype.forEach.call(range.getClientRects(),avoid);
      }
      Array.prototype.forEach.call(chrome.querySelectorAll('img,svg,canvas,video'),function(image){avoid(image.getBoundingClientRect());});
    });
    fitReaderColumnContent(style);
    function align(boundedFit){
      computePageCount();
      return window.LegadoPageAlignment.align(root,{bounds:bounds,paintBounds:paintBounds,
        pageWidth:Math.max(1,root.clientWidth),selector:'p[data-legado-reader-paragraph]',
        boundedFit:!!boundedFit,lastPage:Math.max(1,cachedDocumentPageCount)-1});
    }
    var result=align();
    if(readerColumnFit&&(result.reason||!window.LegadoPageAlignment.textFits(root,bounds,'p,h1,h2,h3,h4,h5,h6'))){
      window.LegadoPageAlignment.clear(root);
      clearReaderColumnFit();markLayoutDirty();
      result=align();result.columnFitFallback=true;
    }
    if(!result.reason&&!result.rejectedPages.length&&result.pages.some(function(page){
      return page.filledBottom&&page.gapAdjustment>Math.max(2,page.lineAdvance*.06);
    })){
      var baseline=result,accepted=false;
      for(var trial=0;trial<2;trial++){
        window.LegadoPageAlignment.clear(root);clearReaderColumnFit();markLayoutDirty();
        var extra=Math.floor((bounds.bottom-bounds.top)*(.04/Math.pow(2,trial))*64)/64;
        if(!fitReaderColumnContent(window.getComputedStyle(root),extra))break;
        var candidate=align(true);
        if(!candidate.reason&&!candidate.rejectedPages.length&&
          window.LegadoPageAlignment.textFits(root,bounds,'p,h1,h2,h3,h4,h5,h6')&&
          candidate.maxGapAdjustment<=baseline.maxGapAdjustment+.1&&candidate.spacingCost<baseline.spacingCost-.0001){
          result=candidate;accepted=true;break;
        }
      }
      if(!accepted){
        window.LegadoPageAlignment.clear(root);clearReaderColumnFit();markLayoutDirty();
        if(!baseline.columnFitFallback)fitReaderColumnContent(window.getComputedStyle(root));
        result=align();result.columnFitFallback=baseline.columnFitFallback;
      }
    }
    result.columnLeading=readerColumnFit?readerColumnFit.leading:0;
    result.columnFitExtra=readerColumnFit?readerColumnFit.extra:0;
    root.__legadoPageAlignment=result;
  }
  markReaderParagraphs();
  alignReaderLineGrid();
  var layoutRtl=rtl;
  function refreshLayoutDirection(){
    try{
      var rootStyle=window.getComputedStyle(root);
      var writingMode=String(rootStyle.writingMode||rootStyle.webkitWritingMode||'').toLowerCase();
      layoutRtl=writingMode.indexOf('vertical-rl')===0||writingMode.indexOf('sideways-rl')===0||
        ((writingMode.indexOf('vertical-lr')!==0&&writingMode.indexOf('sideways-lr')!==0)&&rootStyle.direction==='rtl');
    }catch(_){layoutRtl=rtl;}
  }
  refreshLayoutDirection();
  var cachedPageCount=1;
  var cachedDocumentPageCount=1;
  var windowStartPage=0;
  var windowEndPage=1;
  var layoutDirty=true;
  var renderableDirty=true;
  var cachedRenderableContent=false;
  var layoutRevision=0;
  // A document-local visual version changes when work is queued, before its
  // layout revision is committed. Equal-size image replacements still count.
  var visualRevision=0;
  var sourceImagesPending=0;
  var sharedStyleCache=null;
  var sharedGalleryCache=null;
  var sharedViewportAnchorCache=null;
  var loadedBackgroundImages=Object.create(null);
  var loadedSvgImages=Object.create(null);
  var viewportRenderableRevision=-1;
  var viewportRenderablePage=-1;
  var cachedViewportRenderable=false;
  var viewportTextHints=Object.create(null);
  var readAloudTextIndex=null;
  var layoutRefreshFrame=0;
  var layoutRefreshTimer=0;
  var layoutRefreshRequested=false;
  var layoutRefreshInProgress=false;
  var renderStateFrame=0;
  var reportedRenderToken=null;
  var reportedVisualRevision=-1;
  var reportedLayoutPending=null;
  var deferredLayoutFlushCallback=null;
  var committedViewportAnchor=null;
  var pendingLayoutAnchor=null;
  var pendingLayoutFallback=null;
  var activationBoundaryTarget='';
  var activationTargetRevision=-1;
  var lastLayoutRefreshAt=0;
  var lastLayoutResizeGeometry='';
  var layoutRefreshBaseInterval=__LEGADO_REFRESH_MIN__;
  var layoutRefreshMinInterval=layoutRefreshBaseInterval;
  var layoutRefreshImmediateInterval=__LEGADO_REFRESH_IMMEDIATE__;
  var layoutRefreshMaxInterval=__LEGADO_REFRESH_MAX__;
  var layoutRefreshCostMultiplier=__LEGADO_REFRESH_COST_MULTIPLIER__;
  var rtlScrollType='';
  var horizontalExtentMarker=null;

  function markLayoutDirty(){
    layoutDirty=true;renderableDirty=true;layoutRevision++;
    sharedStyleCache=null;sharedGalleryCache=null;sharedViewportAnchorCache=null;
    viewportTextHints=Object.create(null);
  }

  function isLayoutPending(){
    return !!(layoutRefreshRequested||layoutRefreshFrame||layoutRefreshTimer||
      layoutRefreshInProgress||layoutRefreshDeferredBySelection);
  }

  function reportRenderState(){
    if(!runtimeActive)return;
    var pending=isLayoutPending();
    if(reportedRenderToken===activeToken&&reportedVisualRevision===visualRevision&&
      reportedLayoutPending===pending)return;
    reportedRenderToken=activeToken;reportedVisualRevision=visualRevision;
    reportedLayoutPending=pending;
    var bridge=window.__LEGADO_BRIDGE__;
    if(bridge&&typeof bridge.onRenderState==='function'){
      bridge.onRenderState(activeToken,visualRevision,pending);
    }
  }

  function beginLayoutRefresh(){
    if(!isLayoutPending())visualRevision++;
    layoutRefreshRequested=true;
    // Invalidate native snapshots before source-image attributes/pixels change.
    // Further mutations in this batch share one pending notification.
    reportRenderState();
  }

  function scheduleRenderStateReport(){
    if(!runtimeActive||renderStateFrame)return;
    renderStateFrame=requestAnimationFrame(function(){
      renderStateFrame=0;
      if(!runtimeActive)return;
      reportRenderState();
      if(!isLayoutPending())report();
    });
  }

  function styleCacheForLayout(){
    if(!sharedStyleCache&&window.WeakMap)sharedStyleCache=new WeakMap();
    return sharedStyleCache;
  }

  function galleryCacheForLayout(){
    if(!sharedGalleryCache&&window.WeakMap)sharedGalleryCache=new WeakMap();
    return sharedGalleryCache;
  }

  function viewportAnchorCacheForLayout(){
    if(!sharedViewportAnchorCache&&window.WeakMap)sharedViewportAnchorCache=new WeakMap();
    return sharedViewportAnchorCache;
  }

  function viewportRect(rect){
    return !!(rect&&rect.width>.5&&rect.height>.5&&
      rect.right>.5&&rect.bottom>.5&&
      rect.left<window.innerWidth-.5&&rect.top<window.innerHeight-.5);
  }

  function detectRtlScrollType(){
    if(rtlScrollType)return rtlScrollType;
    var host=document.documentElement||document.body;
    if(!host)return 'negative';
    var probe=document.createElement('div');
    var child=document.createElement('div');
    try{
      probe.setAttribute('dir','rtl');
      probe.style.cssText='position:absolute!important;left:-10000px!important;top:-10000px!important;'+
        'width:4px!important;height:1px!important;overflow:scroll!important;visibility:hidden!important;'+
        'direction:rtl!important;writing-mode:horizontal-tb!important;';
      child.style.cssText='width:8px!important;height:1px!important;';
      probe.appendChild(child);host.appendChild(probe);
      if(probe.scrollLeft>0){
        rtlScrollType='default';
      }else{
        probe.scrollLeft=1;
        rtlScrollType=probe.scrollLeft===0?'negative':'reverse';
      }
    }catch(_){
      rtlScrollType='negative';
    }finally{
      if(probe.parentNode)probe.parentNode.removeChild(probe);
    }
    return rtlScrollType;
  }

  function horizontalScrollOffset(scrolling){
    if(!scrolling)return 0;
    var raw=Number(scrolling.scrollLeft)||0;
    if(!layoutRtl)return Math.max(0,raw);
    var max=Math.max(0,scrolling.scrollWidth-Math.max(1,window.innerWidth));
    var offset;
    switch(detectRtlScrollType()){
      case 'default':offset=max-raw;break;
      case 'reverse':offset=raw;break;
      default:offset=-raw;break;
    }
    return Math.max(0,Math.min(max,offset));
  }

  function setHorizontalScrollOffset(scrolling,offset){
    if(!scrolling)return;
    var max=Math.max(0,scrolling.scrollWidth-Math.max(1,window.innerWidth));
    var target=Math.max(0,Math.min(max,Number(offset)||0));
    if(!layoutRtl){scrolling.scrollLeft=target;return;}
    switch(detectRtlScrollType()){
      case 'default':scrolling.scrollLeft=max-target;break;
      case 'reverse':scrolling.scrollLeft=target;break;
      default:scrolling.scrollLeft=-target;break;
    }
  }

  function detachHorizontalExtentMarker(){
    if(horizontalExtentMarker&&horizontalExtentMarker.parentNode){
      horizontalExtentMarker.parentNode.removeChild(horizontalExtentMarker);
    }
  }

  function mountHorizontalExtentMarker(documentPageCount){
    if(vertical||fixed)return;
    var host=document.documentElement;
    if(!host)return;
    var extent=Math.max(1,window.innerWidth);
    var pages=Math.max(1,Number(documentPageCount)||1);
    var edge=Math.max(0,pages*extent-1);
    var marker=horizontalExtentMarker;
    if(!marker){
      marker=document.createElement('i');
      marker.id='legado-epub-horizontal-extent';
      marker.setAttribute('aria-hidden','true');
      marker.setAttribute('data-legado-runtime','horizontal-extent');
      horizontalExtentMarker=marker;
    }
    marker.style.cssText='display:block!important;position:absolute!important;top:0!important;'+
      'width:1px!important;height:1px!important;margin:0!important;padding:0!important;'+
      'border:0!important;opacity:0!important;pointer-events:none!important;overflow:hidden!important;';
    if(layoutRtl){
      marker.style.setProperty('left','auto','important');
      marker.style.setProperty('right',String(edge)+'px','important');
    }else{
      marker.style.setProperty('right','auto','important');
      marker.style.setProperty('left',String(edge)+'px','important');
    }
    host.appendChild(marker);
  }

  function horizontalViewportPage(){
    var scrolling=document.scrollingElement||document.documentElement;
    var extent=Math.max(1,window.innerWidth);
    return Math.round(horizontalScrollOffset(scrolling)/extent);
  }

  function clampHorizontalRootScroll(){
    if(vertical||fixed||publisherStyled)return false;
    var scrolling=document.scrollingElement||document.documentElement;
    var nodes=[scrolling,document.documentElement,document.body];
    var changed=false;
    for(var i=0;i<nodes.length;i++){
      var node=nodes[i];
      if(!node||nodes.indexOf(node)!==i)continue;
      if(Math.abs(Number(node.scrollTop)||0)>.5){node.scrollTop=0;changed=true;}
    }
    return changed;
  }

  function displayedPageIndex(){
    if(fixed)return 0;
    if(vertical){
      var extent=Math.max(1,window.innerHeight);
      var scrolling=document.scrollingElement||document.documentElement;
      var height=Math.max(scrolling.scrollHeight,root?root.scrollHeight:0,extent);
      var bottom=Math.max(0,height-extent);
      // The last partial screen cannot scroll to pageIndex * extent. Reporting
      // the rounded offset there makes a verified last-page handoff retry forever.
      if(bottom>0&&window.scrollY>=bottom-.5){
        return Math.max(0,Math.ceil((height-.5)/extent)-1)-windowStartPage;
      }
      return Math.round(window.scrollY/extent)-windowStartPage;
    }
    return horizontalViewportPage()-windowStartPage;
  }

  function textAnchorAtPoint(x,y,styleCache,anchorCache){
    var node=null,offset=0;
    try{
      if(document.caretPositionFromPoint){
        var position=document.caretPositionFromPoint(x,y);
        node=position&&position.offsetNode;offset=position&&position.offset||0;
      }else if(document.caretRangeFromPoint){
        var range=document.caretRangeFromPoint(x,y);
        node=range&&range.startContainer;offset=range&&range.startOffset||0;
      }
    }catch(_){}
    if(node&&node.nodeType===Node.TEXT_NODE&&String(node.nodeValue||'').trim()&&
      visibleStyle(node,styleCache)&&!viewportAnchored(node,styleCache,anchorCache)){
      var anchor={kind:'text',node:node,offset:offset,
        source:window.LegadoPageAlignment&&window.LegadoPageAlignment.anchor(node,offset)};
      if(viewportRect(rectForViewportAnchor(anchor)))return anchor;
    }
    return null;
  }

  function elementAnchorAtPoint(x,y,styleCache,anchorCache){
    var elements=[];
    try{
      elements=document.elementsFromPoint?document.elementsFromPoint(x,y):[document.elementFromPoint(x,y)];
    }catch(_){elements=[];}
    for(var i=0;i<elements.length;i++){
      var element=elements[i];
      while(element&&element!==root&&element!==document.documentElement){
        var tag=String(element.tagName||'').toUpperCase();
        if(tag!=='HTML'&&tag!=='BODY'&&visibleStyle(element,styleCache)&&
          !viewportAnchored(element,styleCache,anchorCache)&&
          viewportRect(element.getBoundingClientRect())){
          return {kind:'element',node:element};
        }
        element=element.parentElement;
      }
    }
    return null;
  }

  function captureViewportAnchor(){
    if(!root||fixed)return null;
    var styleCache=styleCacheForLayout();
    var anchorCache=viewportAnchorCacheForLayout();
    var points=[
      [window.innerWidth*.5,window.innerHeight*.5],
      [window.innerWidth*.5,window.innerHeight*.28],
      [window.innerWidth*.5,window.innerHeight*.72],
      [window.innerWidth*.28,window.innerHeight*.5],
      [window.innerWidth*.72,window.innerHeight*.5]
    ];
    for(var i=0;i<points.length;i++){
      var point=points[i];
      var textAnchor=textAnchorAtPoint(point[0],point[1],styleCache,anchorCache);
      if(textAnchor)return textAnchor;
    }
    for(var j=0;j<points.length;j++){
      var fallback=elementAnchorAtPoint(points[j][0],points[j][1],styleCache,anchorCache);
      if(fallback)return fallback;
    }
    return null;
  }

  function rectForViewportAnchor(anchor){
    if(anchor&&anchor.source&&(!anchor.node||!root.contains(anchor.node))&&window.LegadoPageAlignment){
      var resolved=window.LegadoPageAlignment.resolve(anchor.source);
      if(resolved){anchor.node=resolved.node;anchor.offset=resolved.offset;}
    }
    if(!anchor||!anchor.node||!root.contains(anchor.node))return null;
    if(anchor.kind==='text'&&anchor.node.nodeType===Node.TEXT_NODE){
      var length=String(anchor.node.nodeValue||'').length;
      if(length<1)return null;
      var start=Math.max(0,Math.min(length-1,Number(anchor.offset)||0));
      var range=document.createRange();
      try{
        range.setStart(anchor.node,start);range.setEnd(anchor.node,Math.min(length,start+1));
        var rects=range.getClientRects();
        for(var i=0;i<rects.length;i++){
          if(rects[i].width>.5&&rects[i].height>.5)return rects[i];
        }
        return range.getBoundingClientRect();
      }catch(_){return null;}
      finally{if(range.detach)range.detach();}
    }
    return anchor.node.getBoundingClientRect?anchor.node.getBoundingClientRect():null;
  }

  function pageForViewportAnchor(anchor){
    var rect=rectForViewportAnchor(anchor);
    return rect?pageForRect(rect):-1;
  }

  function rememberViewportAnchor(){
    if(!pendingLayoutFallback){
      pendingLayoutFallback={
        page:Math.max(0,displayedPageIndex()),
        pageCount:Math.max(1,cachedPageCount)
      };
    }
    if(!pendingLayoutAnchor){
      pendingLayoutAnchor=committedViewportAnchor&&committedViewportAnchor.node&&
        root.contains(committedViewportAnchor.node)
        ? committedViewportAnchor
        : captureViewportAnchor();
    }
  }

  function commitViewportAnchor(){
    committedViewportAnchor=captureViewportAnchor();
    if(!layoutRefreshInProgress&&isLayoutPending()){
      // An explicit page change supersedes the anchor captured before a queued
      // image/style reflow. Otherwise that stale anchor can undo navigation.
      pendingLayoutAnchor=committedViewportAnchor;
      pendingLayoutFallback={
        page:Math.max(0,displayedPageIndex()),pageCount:Math.max(1,cachedPageCount)
      };
    }
  }

  function fallbackPageAfterLayout(fallback,nextPageCount){
    var oldPage=Math.max(0,Math.min(fallback.pageCount-1,fallback.page));
    if(fallback.pageCount>1&&nextPageCount>1&&fallback.pageCount!==nextPageCount){
      return Math.round(oldPage/Math.max(1,fallback.pageCount-1)*(nextPageCount-1));
    }
    return oldPage;
  }

  function activationBoundaryPage(pageCount){
    var count=Math.max(1,Number(pageCount)||1);
    if(activationBoundaryTarget==='start')return 0;
    if(activationBoundaryTarget==='end')return count-1;
    return -1;
  }

  function runLayoutRefresh(){
    if(!runtimeActive)return;
    if(selectionPageLock){layoutRefreshDeferredBySelection=true;return;}
    layoutRefreshDeferredBySelection=false;
    layoutRefreshRequested=false;
    layoutRefreshInProgress=true;
    try{
    lastLayoutRefreshAt=performance.now();
    var refreshStarted=lastLayoutRefreshAt;
    var anchor=pendingLayoutAnchor;
    var fallback=pendingLayoutFallback||{
      page:Math.max(0,displayedPageIndex()),pageCount:Math.max(1,cachedPageCount)
    };
    pendingLayoutAnchor=null;pendingLayoutFallback=null;
    if(readerLayoutMutationObserver)readerLayoutMutationObserver.disconnect();
    if(window.LegadoPageAlignment)window.LegadoPageAlignment.clear(root);
    refreshLayoutDirection();
    markLayoutDirty();
    markReaderParagraphs();
    alignReaderLineGrid();
    normalizeHorizontalReflow();
    clampHorizontalRootScroll();
    applyFixedScale();
    alignReaderPageGaps();
    invalidateReadAloudTextIndex();
    invalidateOrdinaryTextIndex();
    var nextPageCount=computePageCount();
    var nextPage=activationBoundaryPage(nextPageCount);
    if(nextPage<0)nextPage=pageForViewportAnchor(anchor);
    if(nextPage<0)nextPage=fallbackPageAfterLayout(fallback,nextPageCount);
    setPage(nextPage,0,'auto',activationBoundaryTarget!=='');
    var refreshCost=Math.max(0,performance.now()-refreshStarted);
    var targetInterval=Math.min(
      layoutRefreshMaxInterval,
      Math.max(layoutRefreshBaseInterval,refreshCost*layoutRefreshCostMultiplier)
    );
    layoutRefreshMinInterval=targetInterval>layoutRefreshMinInterval
      ? targetInterval
      : Math.max(layoutRefreshBaseInterval,layoutRefreshMinInterval*.85);
    }finally{
      lastLayoutResizeGeometry=layoutResizeGeometry();
      if(readerLayoutMutationObserver)readerLayoutMutationObserver.observe(root,readerLayoutMutationOptions);
      layoutRefreshInProgress=false;
      scheduleRenderStateReport();
    }
  }

  function appendDeferredLayoutFlushCallback(callback){
    if(typeof callback!=='function')return;
    if(!deferredLayoutFlushCallback){
      deferredLayoutFlushCallback=callback;
      return;
    }
    var previous=deferredLayoutFlushCallback;
    deferredLayoutFlushCallback=function(){previous();callback();};
  }

  function drainDeferredLayoutFlushCallback(){
    if(selectionPageLock||layoutRefreshDeferredBySelection)return;
    var callback=deferredLayoutFlushCallback;
    deferredLayoutFlushCallback=null;
    if(callback)callback();
  }

  function scheduleLayoutRefresh(immediate){
    if(!runtimeActive)return;
    // Styles, fonts and image decoding already converge at publishInitialStable.
    // Rebuilding all highlighted lines before that barrier blocks those resource
    // callbacks and repeats the same expensive chapter layout several times.
    if(!resourcesReady){beginLayoutRefresh();return;}
    immediate=immediate===true;
    rememberViewportAnchor();
    beginLayoutRefresh();
    if(layoutRefreshFrame)return;
    if(immediate&&layoutRefreshTimer){
      clearTimeout(layoutRefreshTimer);layoutRefreshTimer=0;
    }
    var now=performance.now();
    var interval=immediate
      ? Math.min(layoutRefreshMinInterval,layoutRefreshImmediateInterval)
      : layoutRefreshMinInterval;
    var wait=Math.max(0,interval-(now-lastLayoutRefreshAt));
    if(wait>0){
      if(!layoutRefreshTimer)layoutRefreshTimer=setTimeout(function(){
        layoutRefreshTimer=0;scheduleLayoutRefresh(false);
      },wait);
      return;
    }
    layoutRefreshFrame=requestAnimationFrame(function(){
      layoutRefreshFrame=0;
      runLayoutRefresh();
      drainDeferredLayoutFlushCallback();
    });
  }

  function flushLayoutRefresh(callback){
    if(!runtimeActive)return;
    beginLayoutRefresh();
    appendDeferredLayoutFlushCallback(callback);
    if(!resourcesReady)return;
    rememberViewportAnchor();
    if(selectionPageLock){layoutRefreshDeferredBySelection=true;return;}
    if(layoutRefreshTimer){clearTimeout(layoutRefreshTimer);layoutRefreshTimer=0;}
    if(layoutRefreshFrame){cancelAnimationFrame(layoutRefreshFrame);layoutRefreshFrame=0;}
    layoutRefreshFrame=requestAnimationFrame(function(){
      layoutRefreshFrame=0;
      runLayoutRefresh();
      drainDeferredLayoutFlushCallback();
    });
  }

  function rootTranslation(){
    if(vertical||!root)return 0;
    try{
      var transform=window.getComputedStyle(root).transform;
      if(!transform||transform==='none')return 0;
      if(window.DOMMatrixReadOnly)return new DOMMatrixReadOnly(transform).m41||0;
      var match=/^matrix\([^,]+,[^,]+,[^,]+,[^,]+,\s*([^,]+)/.exec(transform);
      return match?Number(match[1])||0:0;
    }catch(_){return 0;}
  }

  function styleFor(element,cache){
    if(cache&&cache.has(element))return cache.get(element);
    var style=window.getComputedStyle(element);
    if(cache)cache.set(element,style);
    return style;
  }

  function visibleStyle(node,cache){
    var element=node&&node.nodeType===Node.ELEMENT_NODE?node:node&&node.parentElement;
    if(!element)return true;
    var style=styleFor(element,cache);
    return style.display!=='none'&&style.visibility!=='hidden'&&Number(style.opacity||1)>0;
  }

  function viewportAnchored(node,styleCache,anchorCache){
    var element=node&&node.nodeType===Node.ELEMENT_NODE?node:node&&node.parentElement;
    if(!element)return false;
    if(isReaderChromeNode(element))return true;
    if(anchorCache&&anchorCache.has(element))return anchorCache.get(element);
    var path=[];
    var current=element;
    var anchored=false;
    while(current){
      if(anchorCache&&anchorCache.has(current)){
        anchored=anchorCache.get(current);break;
      }
      path.push(current);
      var position=String(styleFor(current,styleCache).position||'').toLowerCase();
      if(position==='fixed'||position==='sticky'){
        anchored=true;break;
      }
      if(current===root)break;
      current=current.parentElement;
    }
    if(anchorCache)for(var i=0;i<path.length;i++)anchorCache.set(path[i],anchored);
    return anchored;
  }

  function galleryAncestor(node,cache){
    var element=node&&node.nodeType===Node.ELEMENT_NODE?node:node&&node.parentElement;
    if(!(element&&element.closest))return null;
    if(cache&&cache.has(element))return cache.get(element);
    var gallery=element.closest('.duokan-image-gallery');
    if(cache)cache.set(element,gallery);
    return gallery;
  }

  function runtimeMediaControlNode(node){
    var element=node&&node.nodeType===Node.ELEMENT_NODE?node:node&&node.parentElement;
    if(!element)return false;
    try{return !!(element.closest&&element.closest('[data-legado-runtime="media-control"]'));}catch(_){return false;}
  }

  function runtimeMediaControlMutation(mutation){
    if(!mutation)return false;
    if(runtimeMediaControlNode(mutation.target))return true;
    if(mutation.type!=='childList')return false;
    var nodes=Array.prototype.slice.call(mutation.addedNodes||[]).concat(
      Array.prototype.slice.call(mutation.removedNodes||[])
    );
    return nodes.length>0&&nodes.every(runtimeMediaControlNode);
  }

  function runtimeMediaElement(node){
    var element=node&&node.nodeType===Node.ELEMENT_NODE?node:node&&node.parentElement;
    return !!(element&&element.__legadoDuokanControl);
  }

  function mediaAttributeUrl(media,name){
    var value=String(media&&media.getAttribute&&media.getAttribute(name)||'').trim();
    if(!value)return '';
    try{return new URL(value,media.baseURI||document.baseURI).href;}catch(_){
      var resolver=document.createElement('a');resolver.href=value;return resolver.href||value;
    }
  }

  function prepareDuokanAudio(media){
    if(!media||media.__legadoDuokanControl)return !!(media&&media.__legadoDuokanControl);
    var idleUrl=mediaAttributeUrl(media,'placeholder');
    if(!idleUrl)return false;
    var activeUrl=mediaAttributeUrl(media,'activestate')||idleUrl;
    var control=document.createElement('button');
    control.type='button';
    control.setAttribute('data-legado-runtime','media-control');
    control.setAttribute('aria-label',String(media.getAttribute('title')||'Audio'));
    control.setAttribute('aria-pressed','false');
    control.style.cssText='position:relative!important;display:block!important;box-sizing:border-box!important;'+
      'width:100%!important;max-width:100%!important;margin:0 auto!important;padding:0!important;'+
      'border:0!important;background:transparent!important;box-shadow:none!important;line-height:0!important;'+
      'text-align:center!important;cursor:pointer!important;break-inside:avoid!important;page-break-inside:avoid!important;';
    var idleImage=document.createElement('img');
    idleImage.setAttribute('data-legado-runtime','media-control');
    idleImage.alt='';idleImage.draggable=false;idleImage.src=idleUrl;
    idleImage.style.cssText='display:block!important;width:auto!important;height:auto!important;max-width:100%!important;'+
      'margin:0 auto!important;opacity:1!important;pointer-events:none!important;';
    var activeImage=document.createElement('img');
    activeImage.setAttribute('data-legado-runtime','media-control');
    activeImage.alt='';activeImage.draggable=false;activeImage.src=activeUrl;
    activeImage.style.cssText='position:absolute!important;inset:0!important;display:block!important;width:100%!important;'+
      'height:100%!important;object-fit:contain!important;opacity:0!important;pointer-events:none!important;';
    control.appendChild(idleImage);control.appendChild(activeImage);
    media.parentNode.insertBefore(control,media);
    media.removeAttribute('controls');
    media.setAttribute('playsinline','playsinline');
    media.setAttribute('webkit-playsinline','webkit-playsinline');
    if(!media.getAttribute('preload'))media.setAttribute('preload','metadata');
    media.style.setProperty('display','none','important');
    media.__legadoDuokanControl=control;
    function showPlaying(playing){
      idleImage.style.setProperty('opacity',playing?'0':'1','important');
      activeImage.style.setProperty('opacity',playing?'1':'0','important');
      control.setAttribute('aria-pressed',playing?'true':'false');
    }
    control.addEventListener('click',function(event){
      event.preventDefault();event.stopPropagation();
      if(media.paused||media.ended){
        var pending;
        try{pending=media.play();}catch(_){showPlaying(false);return;}
        if(pending&&typeof pending.catch==='function')pending.catch(function(){showPlaying(false);});
      }else media.pause();
    });
    media.addEventListener('play',function(){showPlaying(true);});
    media.addEventListener('playing',function(){showPlaying(true);});
    media.addEventListener('pause',function(){showPlaying(false);});
    media.addEventListener('ended',function(){showPlaying(false);});
    media.addEventListener('error',function(){showPlaying(false);});
    showPlaying(!media.paused&&!media.ended);
    return true;
  }

  function prepareInteractiveMedia(){
    Array.prototype.forEach.call(document.querySelectorAll('video,audio'),function(media){
      if(String(media.tagName||'').toUpperCase()==='AUDIO'&&prepareDuokanAudio(media))return;
      // Publisher controls and scripted controls are part of the EPUB presentation.
      // Keep them exactly as authored; only add non-visual playback compatibility hints.
      media.setAttribute('playsinline','playsinline');
      media.setAttribute('webkit-playsinline','webkit-playsinline');
      if(!media.getAttribute('preload'))media.setAttribute('preload','metadata');
    });
    Array.prototype.forEach.call(document.querySelectorAll('.duokan-image-gallery'),function(gallery){
      gallery.setAttribute('role','region');
      gallery.setAttribute('aria-roledescription','carousel');
      var cells=gallery.querySelectorAll('.duokan-image-gallery-cell');
      Array.prototype.forEach.call(cells,function(cell,index){
        cell.setAttribute('role','group');
        cell.setAttribute('aria-label',String(index+1)+' / '+String(cells.length));
      });
      var touchX=0,touchY=0,startScroll=0,moved=false,suppressClickUntil=0;
      function galleryImage(target){
        return target&&target.closest?target.closest('.duokan-image-gallery-cell img'):null;
      }
      gallery.addEventListener('touchstart',function(event){
        if(event.touches.length!==1)return;
        touchX=event.touches[0].clientX;
        touchY=event.touches[0].clientY;
        startScroll=gallery.scrollLeft;
        moved=false;
      },{passive:true});
      gallery.addEventListener('touchmove',function(event){
        if(event.touches.length!==1)return;
        var dx=event.touches[0].clientX-touchX;
        var dy=event.touches[0].clientY-touchY;
        if(Math.abs(dx)>=12||Math.abs(dy)>=12||Math.abs(gallery.scrollLeft-startScroll)>=12)moved=true;
      },{passive:true});
      gallery.addEventListener('touchend',function(event){
        if(event.changedTouches.length!==1)return;
        var dx=event.changedTouches[0].clientX-touchX;
        var dy=event.changedTouches[0].clientY-touchY;
        moved=moved||Math.abs(dx)>=12||Math.abs(dy)>=12||Math.abs(gallery.scrollLeft-startScroll)>=12;
        if(moved){suppressClickUntil=Date.now()+500;return;}
      },{passive:true});
      gallery.addEventListener('touchcancel',function(){
        moved=true;suppressClickUntil=Date.now()+500;
      },{passive:true});
      gallery.addEventListener('click',function(event){
        var image=galleryImage(event.target);
        if(!image)return;
        if(Date.now()<suppressClickUntil){event.preventDefault();event.stopPropagation();}
      },false);
    });
  }

  var noteSemantic=/(^|[\s_-])(noteref|footnote|endnote|rearnote|note)([\s_-]|$)/i;
  var noteHint=/(^|[\s_:#-])(fn|ftn|note|footnote|endnote|rearnote)\d*([\s_:#-]|$)/i;
  var noteMarker=/^[\s\[(\uFF08\u3010]?(?:\d{1,4}|[*\u2020\u2021\u00A7\u203B]+)[\s\])\uFF09\u3011]?$/;

  function semanticValue(node){
    if(!node||!node.getAttribute)return '';
    return [node.getAttribute('epub:type')||'',node.getAttribute('type')||'',
      node.getAttribute('role')||'',node.getAttribute('class')||'',node.getAttribute('id')||''].join(' ');
  }

  function isFootnoteLink(anchor){
    if(!anchor)return false;
    if(noteSemantic.test(semanticValue(anchor)))return true;
    var href=anchor.getAttribute('href')||'';
    if(href.indexOf('#')<0||!noteMarker.test(String(anchor.textContent||'').trim()))return false;
    var target=null;
    try{
      var url=new URL(href,document.baseURI);
      if(url.origin===location.origin&&url.pathname===location.pathname){
        var id=decodeURIComponent(url.hash.substring(1));
        target=document.getElementById(id);
      }
    }catch(_){}
    return noteHint.test(href+' '+semanticValue(target))||
      !!(target&&target.querySelector('a[role="doc-backlink"],a[epub\\:type~="backlink"]'));
  }

  function annotationContentTarget(target){
    if(!(target&&target.closest))return target;
    var tag=String(target.localName||target.tagName||'').toLowerCase();
    var marker=String(target.textContent||'').trim();
    if(tag!=='a'||!noteMarker.test(marker)||!noteHint.test(semanticValue(target)))return target;
    var container=target.closest('p,li,dd,dt,aside,section,blockquote');
    if(!container||container===target)return target;
    var content=String(container.textContent||'').trim();
    return content.length>marker.length?container:target;
  }

  function inlineImageFootnoteTarget(target){
    var image=target&&target.closest?target.closest('img.qqreader-footnote'):null;
    return image&&String(image.getAttribute('alt')||'').trim()?image:null;
  }

  var annotationHost=null;
  var annotationShadow=null;
  var annotationPanel=null;
  var annotationBody=null;
  var annotationHeading=null;
  var annotationBackButton=null;
  var annotationStack=[];
  var annotationRequest=0;
  var annotationVisible=false;

  function reportAnnotationState(visible){
    var bridge=window.__LEGADO_BRIDGE__;
    if(bridge&&typeof bridge.onAnnotationState==='function'){
      bridge.onAnnotationState(activeToken,!!visible);
    }
  }

  function setAnnotationVisible(visible){
    visible=!!visible;
    if(annotationVisible===visible)return;
    annotationVisible=visible;
    reportAnnotationState(visible);
  }

  function annotationEventElement(event){
    var path=event&&event.composedPath?event.composedPath():null;
    var target=path&&path.length?path[0]:event&&event.target;
    return target&&target.nodeType===Node.ELEMENT_NODE?target:target&&target.parentElement;
  }

  function isAnnotationBacklink(anchor){
    if(!anchor)return false;
    var rel=anchor.getAttribute('rel')||'';
    var href=anchor.getAttribute('href')||'';
    return /(^|[\s_-])backlink([\s_-]|$)/i.test(rel+' '+semanticValue(anchor))||
      /#[^#]*(?:noteback|backlink|note[-_]?ref|footnote[-_]?ref)/i.test(href);
  }

  function decodeAnnotationFragment(hash){
    var raw=String(hash||'').replace(/^#/,'');
    if(!raw)return '';
    try{return decodeURIComponent(raw);}catch(_){return raw;}
  }

  function annotationDocumentUrl(url){
    var value=new URL(url.href);value.hash='';return value.href;
  }

  function isLocalAnnotationUrl(url){
    try{
      var base=new URL(document.baseURI||location.href);
      return url.origin===base.origin&&!!decodeAnnotationFragment(url.hash);
    }catch(_){return false;}
  }

  function annotationBaseUrl(doc,documentUrl){
    var declared=doc&&doc.querySelector?doc.querySelector('base[href]'):null;
    if(!declared)return documentUrl;
    try{return new URL(declared.getAttribute('href'),documentUrl).href;}catch(_){return documentUrl;}
  }

  function absoluteAnnotationUrl(value,baseUrl,allowDataImage){
    if(!value)return '';
    try{
      var url=new URL(value,baseUrl);var protocol=String(url.protocol||'').toLowerCase();
      if(protocol==='javascript:'||protocol==='vbscript:'||protocol==='file:'||protocol==='content:')return '';
      if(protocol==='data:'&&!(allowDataImage&&/^data:image\//i.test(url.href)))return '';
      return url.href;
    }catch(_){return '';}
  }

  function sanitizeAnnotationTarget(target,baseUrl){
    var clone=document.createElement('div');clone.appendChild(target.cloneNode(true));
    clone.querySelectorAll('script,style,link,meta,base,iframe,frame,object,embed,form,input,select,textarea,button,canvas').forEach(function(node){
      node.remove();
    });
    var nodes=[clone].concat(Array.prototype.slice.call(clone.querySelectorAll('*')));
    nodes.forEach(function(node){
      var tagName=String(node.localName||node.tagName||'').toUpperCase();
      if(tagName==='IMG'){
        var source=node.getAttribute('src')||node.getAttribute('data-src')||node.getAttribute('data-original')||'';
        source=absoluteAnnotationUrl(source,baseUrl,true);
        if(source)node.setAttribute('src',source);
        else node.remove();
      }else if(tagName==='A'&&node.hasAttribute('href')){
        var href=absoluteAnnotationUrl(node.getAttribute('href'),baseUrl,false);
        if(href)node.setAttribute('href',href);else node.removeAttribute('href');
      }else if((tagName==='SOURCE'||tagName==='IMAGE')&&node.hasAttribute('src')){
        var mediaSource=absoluteAnnotationUrl(node.getAttribute('src'),baseUrl,true);
        if(mediaSource)node.setAttribute('src',mediaSource);else node.removeAttribute('src');
      }
      if(tagName==='IMAGE'){
        var imageHref=node.getAttribute('href')||node.getAttribute('xlink:href')||'';
        if(imageHref){
          var absolute=absoluteAnnotationUrl(imageHref,baseUrl,true);
          if(absolute){node.setAttribute('href',absolute);node.setAttribute('xlink:href',absolute);}
          else{node.removeAttribute('href');node.removeAttribute('xlink:href');}
        }
      }
      Array.prototype.slice.call(node.attributes||[]).forEach(function(attribute){
        var name=String(attribute.name||'').toLowerCase();
        if(name.indexOf('on')===0||name==='style'||name==='class'||name==='id'||name==='hidden'||
          name==='aria-hidden'||name==='srcset'||name==='data-src'||name==='data-original'||
          name==='target'||name==='download'||name==='contenteditable'){
          node.removeAttribute(attribute.name);
        }
      });
    });
    clone.querySelectorAll('details').forEach(function(details){details.setAttribute('open','open');});
    if(!String(clone.textContent||'').trim()&&!clone.querySelector('img,svg,math,table,video,audio')){
      throw new Error('annotation content empty');
    }
    return clone;
  }

  function resolveAnnotationTarget(url,rawHref){
    var targetId=decodeAnnotationFragment(url.hash);
    if(!targetId)return Promise.reject(new Error('annotation fragment missing'));
    var currentUrl;
    try{currentUrl=new URL(location.href);}catch(_){currentUrl=new URL(document.baseURI);}
    var sameDocument=String(rawHref||'').trim().charAt(0)==='#'||
      annotationDocumentUrl(url)===annotationDocumentUrl(currentUrl);
    if(sameDocument){
      var localTarget=document.getElementById(targetId);
      if(!localTarget)return Promise.reject(new Error('annotation target missing'));
      return Promise.resolve({
        target:annotationContentTarget(localTarget),
        baseUrl:document.baseURI||annotationDocumentUrl(url),
        documentElement:document.documentElement
      });
    }
    var documentUrl=annotationDocumentUrl(url);
    return fetch(documentUrl,{cache:'no-store',credentials:'omit'}).then(function(response){
      if(!response.ok)throw new Error('annotation request failed: '+response.status);
      return response.text();
    }).then(function(source){
      var doc=new DOMParser().parseFromString(source,'text/html');
      var target=doc.getElementById(targetId);
      if(!target)throw new Error('annotation target missing');
      return {target:annotationContentTarget(target),baseUrl:annotationBaseUrl(doc,documentUrl),documentElement:doc.documentElement};
    });
  }

  function annotationDirection(resolved){
    var value=(resolved.target.getAttribute('dir')||
      (resolved.documentElement&&resolved.documentElement.getAttribute('dir'))||'').toLowerCase();
    if(value==='rtl'||value==='ltr')return value;
    try{return window.getComputedStyle(document.body||document.documentElement).direction||'ltr';}catch(_){return 'ltr';}
  }

  function annotationTitle(resolved){
    return resolved.target.getAttribute('title')||resolved.target.getAttribute('aria-label')||annotationTitleLabel;
  }

  function clearAnnotationBody(){
    if(!annotationBody)return;
    while(annotationBody.firstChild)annotationBody.removeChild(annotationBody.firstChild);
  }

  function renderAnnotation(entry){
    if(!(annotationHost&&annotationBody&&annotationHeading&&annotationBackButton))return;
    clearAnnotationBody();
    annotationBody.appendChild(entry.content);
    annotationHeading.textContent=entry.title||annotationTitleLabel;
    annotationPanel.setAttribute('dir',entry.direction||'ltr');
    annotationBackButton.hidden=annotationStack.length<=1;
    annotationBody.scrollTop=0;
  }

  function closeAnnotation(notify){
    annotationRequest++;
    annotationStack=[];
    clearAnnotationBody();
    if(annotationHost)annotationHost.classList.remove('legado-visible');
    if(notify!==false)setAnnotationVisible(false);else annotationVisible=false;
  }

  function dismissAnnotation(){
    if(!annotationVisible)return false;
    closeAnnotation(true);
    return true;
  }

  function ensureAnnotationOverlay(){
    if(annotationHost)return annotationShadow?annotationHost:null;
    var host=document.createElement('div');host.id='legado-epub-annotation-overlay';
    if(!host.attachShadow)return null;
    var shadow=host.attachShadow({mode:'open'});
    var style=document.createElement('style');
    style.textContent=':host{all:initial!important;position:fixed!important;inset:0!important;z-index:2147483647!important;display:none!important;writing-mode:horizontal-tb!important;font-family:system-ui,-apple-system,sans-serif!important}'+
      ':host(.legado-visible){display:block!important}*{box-sizing:border-box}.backdrop{position:absolute;inset:0;background:rgba(0,0,0,.48)}'+
      '.panel{position:absolute;left:50%;top:50%;transform:translate(-50%,-50%);width:min(92vw,680px);max-height:min(82vh,760px);display:flex;flex-direction:column;overflow:hidden;border-radius:18px;background:var(--legado-bg,#fff);color:var(--legado-fg,#1b1b1b);box-shadow:0 18px 60px rgba(0,0,0,.36);outline:none}'+
      '.header{display:flex;align-items:center;gap:8px;min-height:48px;padding:8px 10px 8px 14px;border-bottom:1px solid rgba(127,127,127,.28)}'+
      '.title{min-width:0;flex:1;font-size:16px;font-weight:650;line-height:1.35;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}'+
      'button{appearance:none;border:0;border-radius:999px;background:transparent;color:inherit;min-width:36px;height:36px;padding:0 10px;font:600 14px/36px system-ui,-apple-system,sans-serif;cursor:pointer}button:hover{background:rgba(127,127,127,.14)}'+
      '.close{font-size:24px;font-weight:400;padding:0}.content{min-height:72px;max-height:calc(min(82vh,760px) - 49px);overflow:auto;overscroll-behavior:contain;touch-action:pan-y;padding:18px 20px 22px;scrollbar-gutter:stable}'+
      '.note{font-size:16px;line-height:1.65;overflow-wrap:anywhere;word-break:break-word;text-align:start}.note>*:first-child{margin-top:0}.note>*:last-child{margin-bottom:0}'+
      '.note p,.note div,.note section,.note aside,.note blockquote{margin:.65em 0}.note img,.note svg{display:block;max-width:100%;height:auto;margin:12px auto;border-radius:8px}.note table{display:block;max-width:100%;overflow:auto;border-collapse:collapse}.note td,.note th{padding:6px;border:1px solid rgba(127,127,127,.32)}.note a{color:inherit;text-decoration:underline;text-underline-offset:2px}'+
      '.loading{padding:24px;text-align:center;opacity:.7}@media(max-width:480px){.panel{width:calc(100vw - 24px);border-radius:16px}.content{padding:16px}}';
    var backdrop=document.createElement('div');backdrop.className='backdrop';
    var panel=document.createElement('section');panel.className='panel';panel.tabIndex=-1;panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','true');
    var header=document.createElement('header');header.className='header';
    var back=document.createElement('button');back.className='back';back.type='button';back.textContent=annotationBackLabel;back.hidden=true;
    var heading=document.createElement('div');heading.className='title';heading.id='legado-annotation-title';heading.setAttribute('role','heading');heading.setAttribute('aria-level','2');heading.textContent=annotationTitleLabel;panel.setAttribute('aria-labelledby',heading.id);
    var close=document.createElement('button');close.className='close';close.type='button';close.textContent='×';close.setAttribute('aria-label',annotationCloseLabel);
    var content=document.createElement('div');content.className='content';
    var body=document.createElement('article');body.className='note';content.appendChild(body);
    header.appendChild(back);header.appendChild(heading);header.appendChild(close);panel.appendChild(header);panel.appendChild(content);
    shadow.appendChild(style);shadow.appendChild(backdrop);shadow.appendChild(panel);document.documentElement.appendChild(host);
    backdrop.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();closeAnnotation(true);},true);
    close.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();closeAnnotation(true);},true);
    back.addEventListener('click',function(e){
      e.preventDefault();e.stopPropagation();
      if(annotationStack.length<=1){closeAnnotation(true);return;}
      annotationStack.pop();renderAnnotation(annotationStack[annotationStack.length-1]);
    },true);
    shadow.addEventListener('click',function(e){
      var element=annotationEventElement(e);if(!element||element===backdrop||element===back||element===close)return;
      var anchor=element.closest?element.closest('a[href]'):null;
      if(anchor){
        e.preventDefault();e.stopPropagation();
        if(isAnnotationBacklink(anchor)){closeAnnotation(true);return;}
        if(isFootnoteLink(anchor)){showAnnotation(anchor);return;}
        var href=anchor.href;closeAnnotation(true);
        var bridge=window.__LEGADO_BRIDGE__;if(bridge&&typeof bridge.onLink==='function')bridge.onLink(activeToken,href);
        return;
      }
      var image=element.closest?element.closest('img'):null;
      if(image){
        e.preventDefault();e.stopPropagation();
        var src=image.currentSrc||image.src||'';
        if(src){imageOverlay(src);var bridge=window.__LEGADO_BRIDGE__;if(bridge&&typeof bridge.onImage==='function')bridge.onImage(activeToken,src);}
      }
    },true);
    annotationHost=host;annotationShadow=shadow;annotationPanel=panel;annotationBody=body;
    annotationHeading=heading;annotationBackButton=back;
    return host;
  }

  function showAnnotation(anchor){
    var rawHref=anchor&&anchor.getAttribute?anchor.getAttribute('href')||'':'';
    var url;
    try{url=new URL(anchor&&anchor.href?anchor.href:rawHref,document.baseURI);}catch(_){return false;}
    if(!isLocalAnnotationUrl(url)||!ensureAnnotationOverlay())return false;
    var request=++annotationRequest;
    annotationHost.classList.add('legado-visible');setAnnotationVisible(true);
    try{annotationPanel.focus({preventScroll:true});}catch(_){annotationPanel.focus();}
    if(annotationStack.length===0){
      clearAnnotationBody();var loading=document.createElement('div');loading.className='loading';loading.textContent='…';annotationBody.appendChild(loading);
      annotationHeading.textContent=annotationTitleLabel;annotationBackButton.hidden=true;
    }
    resolveAnnotationTarget(url,rawHref).then(function(resolved){
      if(request!==annotationRequest||!annotationVisible)return;
      var entry={
        url:url.href,
        title:annotationTitle(resolved),
        direction:annotationDirection(resolved),
        content:sanitizeAnnotationTarget(resolved.target,resolved.baseUrl)
      };
      var previous=annotationStack.length?annotationStack[annotationStack.length-1]:null;
      if(previous&&previous.url===entry.url)annotationStack[annotationStack.length-1]=entry;
      else annotationStack.push(entry);
      renderAnnotation(entry);
    }).catch(function(){
      if(request!==annotationRequest)return;
      closeAnnotation(true);
      var bridge=window.__LEGADO_BRIDGE__;if(bridge&&typeof bridge.onFootnote==='function')bridge.onFootnote(activeToken,url.href);
    });
    return true;
  }

  function showInlineImageFootnote(image){
    var text=String(image&&image.getAttribute?image.getAttribute('alt')||'':'').trim();
    if(!text||!ensureAnnotationOverlay())return false;
    annotationRequest++;
    annotationStack=[];
    var content=document.createElement('div');content.textContent=text;
    var entry={
      url:'inline-image-footnote:'+annotationRequest,
      title:annotationTitleLabel,
      direction:annotationDirection({target:image,documentElement:document.documentElement}),
      content:content
    };
    annotationHost.classList.add('legado-visible');setAnnotationVisible(true);
    annotationStack.push(entry);renderAnnotation(entry);
    try{annotationPanel.focus({preventScroll:true});}catch(_){annotationPanel.focus();}
    return true;
  }

  var embeddedTouch=null;
  var embeddedInteractionSerial=0;
  var scrollOverflow=/^(auto|scroll|overlay)$/;

  function reportEmbeddedTouch(active){
    if(!embeddedTouch||embeddedTouch.active===active)return;
    embeddedTouch.active=active;
    window.__LEGADO_BRIDGE__.onEmbeddedInteraction(
      activeToken,embeddedTouch.id,active
    );
  }

  function interactiveTarget(target){
    var element=target&&target.nodeType===Node.ELEMENT_NODE?target:target&&target.parentElement;
    if(!(element&&element.closest))return null;
    var hard=!!element.closest(
      '#legado-epub-image-overlay,#legado-epub-annotation-overlay,.duokan-image-gallery,img.qqreader-footnote,video,audio,button,input,select,textarea,summary,label,iframe,embed,object,[contenteditable]:not([contenteditable="false"]),[role="button"],[role="slider"],[role="spinbutton"],[role="textbox"],[draggable="true"]'
    );
    var sourceImage=textReaderImageTarget(element);
    // Ordinary source images own taps, not drags. Claiming their generated
    // anchor at DOWN prevents native page turns for the entire touch sequence.
    // Real controls/overlays and nested scrollers keep their gesture ownership.
    if(!sourceImage&&element.closest('a[href]'))hard=true;
    var scrollers=[];
    var node=element;
    while(node&&node!==root&&node!==document.documentElement){
      var style=window.getComputedStyle(node);
      var overflowX=Math.max(0,node.scrollWidth-node.clientWidth);
      var overflowY=Math.max(0,node.scrollHeight-node.clientHeight);
      var scrollX=scrollOverflow.test(style.overflowX)&&overflowX>1;
      var scrollY=scrollOverflow.test(style.overflowY)&&overflowY>1;
      if(scrollX||scrollY)scrollers.push({
        node:node,rtl:style.direction==='rtl',x:scrollX,y:scrollY,
        overflowX:overflowX,overflowY:overflowY
      });
      node=node.parentElement;
    }
    return hard||scrollers.length?{hard:hard,scrollers:scrollers}:null;
  }

  function scrollableForGesture(entry,dx,dy){
    var horizontal=Math.abs(dx)>Math.abs(dy);
    if(horizontal&&entry.x){
      var min=entry.rtl?-entry.overflowX:0;
      var max=entry.rtl?0:entry.overflowX;
      var desired=-dx;
      return desired<0
        ? entry.node.scrollLeft>min+1
        : desired>0&&entry.node.scrollLeft<max-1;
    }
    if(!horizontal&&entry.y){
      var verticalDesired=-dy;
      return verticalDesired<0
        ? entry.node.scrollTop>1
        : verticalDesired>0&&entry.node.scrollTop<entry.overflowY-1;
    }
    return false;
  }

  function findTouch(touches,identifier){
    for(var i=0;i<touches.length;i++)if(touches[i].identifier===identifier)return touches[i];
    return null;
  }

  function finishEmbeddedTouch(event){
    if(!embeddedTouch)return;
    if(event&&event.changedTouches&&
      !findTouch(event.changedTouches,embeddedTouch.identifier))return;
    reportEmbeddedTouch(false);
    embeddedTouch=null;
  }

  function fragmentTarget(id){
    id=String(id||'').replace(/^#/,'');
    if(!id)return null;
    var ids=[id];
    try{
      var decoded=decodeURIComponent(id);
      if(decoded&&decoded!==id)ids.push(decoded);
    }catch(_){}
    for(var candidateIndex=0;candidateIndex<ids.length;candidateIndex++){
      var candidateId=ids[candidateIndex];
      var target=document.getElementById(candidateId);
      if(target)return target;
      var named=document.getElementsByName?document.getElementsByName(candidateId):[];
      for(var n=0;n<named.length;n++)if(String(named[n].tagName||'').toUpperCase()==='A')return named[n];
      var xmlIds=document.querySelectorAll?document.querySelectorAll('[xml\\:id]'):[];
      for(var i=0;i<xmlIds.length;i++)if(xmlIds[i].getAttribute('xml:id')===candidateId)return xmlIds[i];
    }
    return null;
  }

  function fragmentWindowTargets(){
    if(!root||(!startId&&!endId))return null;
    var start=startId?fragmentTarget(startId):null;
    var end=endId&&endId!==startId?fragmentTarget(endId):null;
    if(start&&!root.contains(start))start=null;
    if(end&&!root.contains(end))end=null;
    if(startId&&!start)return null;
    if(start&&end&&(start===end||(start.compareDocumentPosition(end)&Node.DOCUMENT_POSITION_PRECEDING)))end=null;
    return start||end?{start:start,end:end}:null;
  }

  function targetInsideFragmentWindow(target,targets){
    if(!target||!targets)return true;
    if(targets.start&&target!==targets.start&&
      (targets.start.compareDocumentPosition(target)&Node.DOCUMENT_POSITION_PRECEDING))return false;
    if(targets.end&&(target===targets.end||
      (targets.end.compareDocumentPosition(target)&Node.DOCUMENT_POSITION_FOLLOWING)))return false;
    return true;
  }

  function fragmentRect(target){
    if(!target)return null;
    var rects=target.getClientRects?target.getClientRects():[];
    for(var i=0;i<rects.length;i++){
      if(rects[i].width>.5||rects[i].height>.5)return rects[i];
    }
    return target.getBoundingClientRect?target.getBoundingClientRect():null;
  }

  function previousRenderablePage(target){
    if(!target||!root)return null;
    var walker=document.createTreeWalker(root,NodeFilter.SHOW_ELEMENT|NodeFilter.SHOW_TEXT);
    var styleCache=styleCacheForLayout();
    var anchorCache=viewportAnchorCacheForLayout();
    var range=document.createRange();
    var checked=0;
    var node;
    walker.currentNode=target;
    while(checked++<128&&(node=walker.previousNode())){
      var rects=null;
      if(node.nodeType===Node.TEXT_NODE){
        if(!String(node.nodeValue||'').trim()||!visibleStyle(node,styleCache)||
          viewportAnchored(node,styleCache,anchorCache))continue;
        range.selectNodeContents(node);
        rects=range.getClientRects();
      }else if(/^(IMG|SVG|IMAGE|VIDEO|CANVAS|TABLE|OBJECT|IFRAME|EMBED|HR)$/i.test(String(node.tagName||''))){
        if(!visibleStyle(node,styleCache)||viewportAnchored(node,styleCache,anchorCache))continue;
        rects=[node.getBoundingClientRect()];
      }else{
        continue;
      }
      var page=null;
      for(var i=0;i<rects.length;i++){
        if(rects[i].width<=.5||rects[i].height<=.5)continue;
        var candidate=absolutePageForRect(rects[i]);
        page=page==null?candidate:Math.max(page,candidate);
      }
      if(page!=null){if(range.detach)range.detach();return page;}
    }
    if(range.detach)range.detach();
    return null;
  }

  function fragmentPageWindow(targets){
    if(!targets)return null;
    var end=null;
    if(targets.end){
      var endPage=absolutePageForRect(fragmentRect(targets.end));
      end=previousRenderablePage(targets.end)===endPage?endPage+1:endPage;
    }
    return {
      start:targets.start?absolutePageForRect(fragmentRect(targets.start)):0,
      end:end
    };
  }

  function applyPageWindow(documentPageCount,fragmentWindow){
    cachedDocumentPageCount=Math.max(1,documentPageCount);
    windowStartPage=fragmentWindow
      ? Math.max(0,Math.min(cachedDocumentPageCount-1,fragmentWindow.start))
      : 0;
    windowEndPage=fragmentWindow&&fragmentWindow.end!=null
      ? Math.max(windowStartPage+1,Math.min(cachedDocumentPageCount,fragmentWindow.end))
      : cachedDocumentPageCount;
    cachedPageCount=Math.max(1,windowEndPage-windowStartPage);
  }

  function applyFixedScale(){
    if(!scaled||!root)return;
    var sw=__LEGADO_SOURCE_WIDTH__||root.scrollWidth||window.innerWidth;
    var sh=__LEGADO_SOURCE_HEIGHT__||root.scrollHeight||window.innerHeight;
    var density=Math.max(1,Number(window.devicePixelRatio)||1);
    var left=publisherFullscreen?0:readerPaddingLeftPx/density;
    var top=publisherFullscreen?0:readerPaddingTopPx/density;
    var right=publisherFullscreen?0:readerPaddingRightPx/density;
    var bottom=publisherFullscreen?0:readerPaddingBottomPx/density;
    var availableWidth=Math.max(1,window.innerWidth-left-right);
    var availableHeight=Math.max(1,window.innerHeight-top-bottom);
    var scale=Math.max(.05,Math.min(8,Math.min(availableWidth/Math.max(1,sw),availableHeight/Math.max(1,sh))));
    root.style.setProperty('left','calc(50% + '+String((left-right)/2)+'px)','important');
    root.style.setProperty('top','calc(50% + '+String((top-bottom)/2)+'px)','important');
    root.style.setProperty('--legado-fixed-scale',String(scale));
  }

  var horizontalReflowCandidateLimit=24;
  var horizontalReflowDepthLimit=8;
  var horizontalReflowIgnoredTag=/^(SCRIPT|STYLE|LINK|META|BASE|TITLE|TEMPLATE|NOSCRIPT)$/i;

  function soleHorizontalReflowChild(node){
    var child=null;
    var children=node&&node.childNodes?node.childNodes:[];
    for(var i=0;i<children.length;i++){
      var candidate=children[i];
      if(candidate.nodeType===Node.TEXT_NODE){
        if(String(candidate.nodeValue||'').trim())return null;
        continue;
      }
      if(candidate.nodeType!==Node.ELEMENT_NODE||
        horizontalReflowIgnoredTag.test(String(candidate.tagName||'')))continue;
      if(child)return null;
      child=candidate;
    }
    return child;
  }

  function normalizeHorizontalReflow(){
    if(vertical||fixed||publisherStyled||!root)return;
    var changed=false;
    var queue=[];
    var rootChildren=root.children||[];
    for(var i=0;i<rootChildren.length&&queue.length<horizontalReflowCandidateLimit;i++){
      var rootChild=rootChildren[i];
      if(!horizontalReflowIgnoredTag.test(String(rootChild.tagName||''))&&!isReaderChromeNode(rootChild)){
        queue.push({node:rootChild,depth:1});
      }
    }
    var cursor=0;
    while(cursor<queue.length&&cursor<horizontalReflowCandidateLimit){
      var entry=queue[cursor++];
      var node=entry.node;
      if(node.closest('.duokan-image-gallery,form,[contenteditable]:not([contenteditable="false"]),[role="dialog"],[role="listbox"]'))continue;
      var style=window.getComputedStyle(node);
      if(!style||style.position==='fixed')continue;
      var scrollY=/^(auto|scroll|overlay)$/.test(style.overflowY)&&node.scrollHeight>node.clientHeight+1;
      if(scrollY){
        var rect=node.getBoundingClientRect();
        var fullPageWrapper=rect.width>=window.innerWidth*.72&&rect.height>=window.innerHeight*.9;
        if(fullPageWrapper){
          node.style.setProperty('height','auto','important');
          node.style.setProperty('max-height','none','important');
          node.style.setProperty('overflow-y','visible','important');
          changed=true;
        }
      }
      if(entry.depth<horizontalReflowDepthLimit&&queue.length<horizontalReflowCandidateLimit){
        var child=soleHorizontalReflowChild(node);
        if(child)queue.push({node:child,depth:entry.depth+1});
      }
    }
    if(changed)markLayoutDirty();
  }

  function computePageCount(){
    if(fixed){
      detachHorizontalExtentMarker();
      applyPageWindow(1,null);layoutDirty=false;return 1;
    }
    if(!layoutDirty)return cachedPageCount;
    var docRoot=document.scrollingElement||document.documentElement;
    var extent=Math.max(1,vertical?window.innerHeight:window.innerWidth);
    var preservedHorizontalOffset=vertical?0:horizontalScrollOffset(docRoot);
    detachHorizontalExtentMarker();
    var fragmentWindow=fragmentPageWindow(fragmentWindowTargets());
    if(fragmentWindow&&fragmentWindow.end!=null){
      var cheapExtent=vertical
        ? Math.max(docRoot.scrollHeight,root?root.scrollHeight:0,window.innerHeight)
        : Math.max(docRoot.scrollWidth,root?root.scrollWidth:0,window.innerWidth);
      applyPageWindow(
        Math.max(fragmentWindow.end,Math.ceil((cheapExtent-.5)/extent)),
        fragmentWindow
      );
      if(!vertical){
        mountHorizontalExtentMarker(cachedDocumentPageCount);
        setHorizontalScrollOffset(docRoot,preservedHorizontalOffset);
      }
      layoutDirty=false;
      return cachedPageCount;
    }
    if(vertical){
      var height=Math.max(docRoot.scrollHeight,root?root.scrollHeight:0,window.innerHeight);
      applyPageWindow(Math.max(1,Math.ceil((height-.5)/extent)),fragmentWindow);
      layoutDirty=false;
      return cachedPageCount;
    }
    var translation=rootTranslation();
    var maxPage=-1;
    var styleCache=styleCacheForLayout();
    var galleryCache=galleryCacheForLayout();
    var anchorCache=viewportAnchorCacheForLayout();
    function includeRect(rect){
      if(!rect||rect.width<=0||rect.height<=0)return -1;
      var start=rect.left+window.scrollX-translation;
      var end=rect.right+window.scrollX-translation;
      var page=layoutRtl
        ? Math.max(0,Math.ceil((extent-start-.5)/extent)-1)
        : Math.max(0,Math.ceil((end-.5)/extent)-1);
      maxPage=Math.max(maxPage,page);
      return page;
    }
    var walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);
    var textRange=document.createRange();
    var node;
    while(node=walker.nextNode()){
      if(!String(node.nodeValue||'').trim()||
        !visibleStyle(node,styleCache)||galleryAncestor(node,galleryCache)||
        viewportAnchored(node,styleCache,anchorCache))continue;
      textRange.selectNodeContents(node);
      // Pagination already measures these fragments. Keep a text candidate for
      // each page so a later turn does not rescan every preceding paragraph.
      Array.prototype.forEach.call(textRange.getClientRects(),function(rect){
        var page=includeRect(rect);
        if(page>=0&&!viewportTextHints[page])viewportTextHints[page]=node;
      });
    }
    if(textRange.detach)textRange.detach();
    Array.prototype.forEach.call(
      document.querySelectorAll('img,svg,image,video,canvas,table,object'),
      function(element){if(visibleStyle(element,styleCache)&&!galleryAncestor(element,galleryCache)&&
        !viewportAnchored(element,styleCache,anchorCache))includeRect(element.getBoundingClientRect());}
    );
    Array.prototype.forEach.call(document.querySelectorAll('.duokan-image-gallery'),function(gallery){
      if(visibleStyle(gallery,styleCache)&&!viewportAnchored(gallery,styleCache,anchorCache))includeRect(gallery.getBoundingClientRect());
    });
    Array.prototype.forEach.call(document.querySelectorAll('body *'),function(element){
      if(!visibleStyle(element,styleCache)||galleryAncestor(element,galleryCache)||
        viewportAnchored(element,styleCache,anchorCache))return;
      var style=styleFor(element,styleCache);
      if(style.backgroundImage&&style.backgroundImage!=='none')includeRect(element.getBoundingClientRect());
    });
    var documentPageCount;
    if(maxPage>=0){
      documentPageCount=maxPage+1;
    }else{
      var width=Math.max(docRoot.scrollWidth,root?root.scrollWidth:0,window.innerWidth);
      documentPageCount=Math.max(1,Math.ceil((width-.5)/extent));
    }
    applyPageWindow(documentPageCount,fragmentWindow);
    mountHorizontalExtentMarker(cachedDocumentPageCount);
    setHorizontalScrollOffset(docRoot,preservedHorizontalOffset);
    layoutDirty=false;
    return cachedPageCount;
  }

  function rectInsidePageWindow(rect){
    if(!rect||rect.width<=.5||rect.height<=.5)return false;
    var extent=Math.max(1,vertical?window.innerHeight:window.innerWidth);
    if(vertical){
      var top=rect.top+window.scrollY;
      var bottom=rect.bottom+window.scrollY;
      return bottom>windowStartPage*extent+.5&&top<windowEndPage*extent-.5;
    }
    var left=rect.left+window.scrollX-rootTranslation();
    var right=rect.right+window.scrollX-rootTranslation();
    var windowLeft=layoutRtl?(1-windowEndPage)*extent:windowStartPage*extent;
    var windowRight=layoutRtl?(1-windowStartPage)*extent:windowEndPage*extent;
    return right>windowLeft+.5&&left<windowRight-.5;
  }

  function imageHasPixels(image){
    return !!(image&&image.complete&&Number(image.naturalWidth)>0&&Number(image.naturalHeight)>0);
  }

  function svgImageUrl(image){
    if(!image)return '';
    var value=image.getAttribute('href')||
      image.getAttributeNS&&image.getAttributeNS('http://www.w3.org/1999/xlink','href')||'';
    if(!value)return '';
    try{return new URL(value,document.baseURI).href;}catch(_){return String(value);}
  }

  function svgImageHasPixels(image){
    var url=svgImageUrl(image);
    return !!url&&loadedSvgImages[url]===true;
  }

  function svgHasPixels(svg){
    if(svg.querySelector('path,rect,circle,ellipse,line,polyline,polygon,text,tspan,use,foreignObject'))return true;
    var images=svg.querySelectorAll('image');
    if(!images.length)return true;
    for(var i=0;i<images.length;i++)if(svgImageHasPixels(images[i]))return true;
    return false;
  }

  function visualHasPixels(element){
    var tag=String(element&&element.tagName||'').toUpperCase();
    if(tag==='IMG')return imageHasPixels(element);
    if(tag==='IMAGE')return svgImageHasPixels(element);
    if(tag==='SVG')return svgHasPixels(element);
    if(element&&element.classList&&element.classList.contains('duokan-image-gallery')){
      var galleryImages=element.querySelectorAll('img');
      if(galleryImages.length){
        for(var i=0;i<galleryImages.length;i++)if(imageHasPixels(galleryImages[i]))return true;
        return false;
      }
    }
    return true;
  }

  function backgroundImageUrls(value){
    var urls=[];
    var match;
    var pattern=/url\(\s*(["']?)(.*?)\1\s*\)/g;
    while((match=pattern.exec(String(value||'')))){
      var url=String(match[2]||'').trim();
      if(url)urls.push(url);
    }
    return urls;
  }

  function backgroundHasPixels(value){
    value=String(value||'');
    if(!value||value==='none')return false;
    if(/(?:repeating-)?(?:linear|radial|conic)-gradient\(/i.test(value))return true;
    var urls=backgroundImageUrls(value);
    if(!urls.length)return true;
    for(var i=0;i<urls.length;i++)if(loadedBackgroundImages[urls[i]]===true)return true;
    return false;
  }

  function computeRenderableContent(){
    if(!renderableDirty)return cachedRenderableContent;
    cachedRenderableContent=false;
    if(!root){renderableDirty=false;return false;}
    var styleCache=styleCacheForLayout();
    var anchorCache=viewportAnchorCacheForLayout();
    var walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);
    var textRange=document.createRange();
    var node;
    while(node=walker.nextNode()){
      if(!String(node.nodeValue||'').trim()||!visibleStyle(node,styleCache)||
        viewportAnchored(node,styleCache,anchorCache))continue;
      textRange.selectNodeContents(node);
      var rects=textRange.getClientRects();
      for(var i=0;i<rects.length;i++){
        if(rectInsidePageWindow(rects[i])){cachedRenderableContent=true;break;}
      }
      if(cachedRenderableContent)break;
    }
    if(textRange.detach)textRange.detach();
    if(!cachedRenderableContent){
      var visuals=document.querySelectorAll(
        'img,svg,image,video,audio,canvas,table,object,iframe,embed,button,input,select,textarea,hr,.duokan-image-gallery'
      );
      for(var j=0;j<visuals.length;j++){
        if(visualHasPixels(visuals[j])&&visibleStyle(visuals[j],styleCache)&&
          !viewportAnchored(visuals[j],styleCache,anchorCache)&&
          rectInsidePageWindow(visuals[j].getBoundingClientRect())){
          cachedRenderableContent=true;break;
        }
      }
    }
    if(!cachedRenderableContent){
      var elements=[root].concat(Array.prototype.slice.call(root.querySelectorAll('*')));
      for(var k=0;k<elements.length;k++){
        if(!visibleStyle(elements[k],styleCache)||
          viewportAnchored(elements[k],styleCache,anchorCache))continue;
        var style=styleFor(elements[k],styleCache);
        if(backgroundHasPixels(style.backgroundImage)&&
          rectInsidePageWindow(elements[k].getBoundingClientRect())){
          cachedRenderableContent=true;break;
        }
      }
    }
    renderableDirty=false;
    return cachedRenderableContent;
  }

  function computeViewportRenderable(){
    if(!root)return false;
    if(viewportRenderableRevision===layoutRevision&&viewportRenderablePage===currentPage){
      return cachedViewportRenderable;
    }
    var styleCache=styleCacheForLayout();
    var anchorCache=viewportAnchorCacheForLayout();
    function cacheResult(value){
      viewportRenderableRevision=layoutRevision;
      viewportRenderablePage=currentPage;
      cachedViewportRenderable=value;
      return value;
    }
    var textRange=document.createRange();
    function textInViewport(node){
      if(!node||!root.contains(node)||!String(node.nodeValue||'').trim()||
        !visibleStyle(node,styleCache)||viewportAnchored(node,styleCache,anchorCache))return false;
      textRange.selectNodeContents(node);
      var rects=textRange.getClientRects();
      for(var i=0;i<rects.length;i++){
        if(viewportRect(rects[i]))return true;
      }
      return false;
    }
    // A hint is only an optimization: validate live geometry before accepting
    // it, and retain the full text/image/background check for empty or moved pages.
    var absolutePage=windowStartPage+currentPage;
    var hint=viewportTextHints[absolutePage];
    var found=textInViewport(hint);
    if(!found){
      var walker=document.createTreeWalker(root,NodeFilter.SHOW_TEXT);
      var node;
      while(node=walker.nextNode()){
        if(node!==hint&&textInViewport(node)){
          viewportTextHints[absolutePage]=node;found=true;break;
        }
      }
    }
    if(textRange.detach)textRange.detach();
    if(found)return cacheResult(true);
    var visuals=document.querySelectorAll(
      'img,svg,image,video,audio,canvas,table,object,iframe,embed,button,input,select,textarea,hr,.duokan-image-gallery'
    );
    for(var j=0;j<visuals.length;j++){
      if(visualHasPixels(visuals[j])&&visibleStyle(visuals[j],styleCache)&&
        !viewportAnchored(visuals[j],styleCache,anchorCache)&&
        viewportRect(visuals[j].getBoundingClientRect()))return cacheResult(true);
    }
    var elements=[root].concat(Array.prototype.slice.call(root.querySelectorAll('*')));
    for(var k=0;k<elements.length;k++){
      if(!visibleStyle(elements[k],styleCache)||
        viewportAnchored(elements[k],styleCache,anchorCache))continue;
      var style=styleFor(elements[k],styleCache);
      if(backgroundHasPixels(style.backgroundImage)&&
        viewportRect(elements[k].getBoundingClientRect()))return cacheResult(true);
    }
    return cacheResult(false);
  }

  function positionMetrics(){
    var count=computePageCount();
    currentPage=displayedPageIndex();
    currentPage=Math.max(0,Math.min(count-1,currentPage));
    return {pageCount:count,pageIndex:currentPage};
  }

  function metrics(checkViewport){
    var position=positionMetrics();
    var boundaryPage=activationBoundaryPage(position.pageCount);
    return {
      pageCount:position.pageCount,
      pageIndex:position.pageIndex,
      ready:stable,
      resourcesReady:resourcesReady,
      resourcesFailed:resourcesFailed,
      layoutRevision:layoutRevision,
      visualRevision:visualRevision,
      layoutPending:isLayoutPending(),
      sourceImagesPending:sourceImagesPending,
      activationTargetRevision:activationTargetRevision,
      activationTargetSatisfied:boundaryPage<0||(
        activationTargetRevision===layoutRevision&&position.pageIndex===boundaryPage
      ),
      renderable:computeRenderableContent(),
      viewportRenderable:checkViewport===true?computeViewportRenderable():false,
      documentPageCount:cachedDocumentPageCount,
      windowStartPage:windowStartPage,
      windowEndPage:windowEndPage
    };
  }

  function report(){
    if(!runtimeActive)return;
    clearTimeout(timer);
    timer=setTimeout(function(){
      if(!runtimeActive)return;
      reportRenderState();
      // Page metrics describe committed layout. The pending notification owns
      // invalidation until the final coalesced refresh schedules this report.
      if(isLayoutPending())return;
      var m=positionMetrics();
      committedViewportAnchor=captureViewportAnchor();
      window.__LEGADO_BRIDGE__.onMetrics(activeToken,m.pageCount,m.pageIndex,layoutRevision);
      if(root.hasAttribute('data-legado-text-reader')&&window.__LEGADO_BRIDGE__.onTextPosition){
        window.__LEGADO_BRIDGE__.onTextPosition(activeToken,m.pageIndex,layoutRevision,textReaderPosition(m.pageIndex));
      }
    },24);
  }

  function setHorizontalScrollPage(animationMs){
    var scrolling=document.scrollingElement||document.documentElement;
    if(!scrolling)return false;
    clampHorizontalRootScroll();
    if(horizontalScrollAnimationFrame){
      cancelAnimationFrame(horizontalScrollAnimationFrame);horizontalScrollAnimationFrame=0;
    }
    var targetOffset=(windowStartPage+currentPage)*Math.max(1,window.innerWidth);
    var max=Math.max(0,scrolling.scrollWidth-Math.max(1,window.innerWidth));
    targetOffset=Math.max(0,Math.min(max,targetOffset));
    var target=layoutRtl?(
      detectRtlScrollType()==='default'?max-targetOffset:
        detectRtlScrollType()==='reverse'?targetOffset:-targetOffset
    ):targetOffset;
    var start=Number(scrolling.scrollLeft)||0;
    if(animationMs<=0||Math.abs(target-start)<1){
      readerChromeMotionPending=false;
      setHorizontalScrollOffset(scrolling,targetOffset);
      clampHorizontalRootScroll();
      return false;
    }
    readerChromeMotionPending=true;
    var startedAt=performance.now();
    function step(now){
      var elapsed=Math.max(0,now-startedAt);
      var progress=Math.min(1,elapsed/animationMs);
      var eased=1-Math.pow(1-progress,3);
      scrolling.scrollLeft=start+(target-start)*eased;
      if(progress<1){horizontalScrollAnimationFrame=requestAnimationFrame(step);}
      else{
        horizontalScrollAnimationFrame=0;readerChromeMotionPending=false;
        setHorizontalScrollOffset(scrolling,targetOffset);positionReaderChrome();
        commitViewportAnchor();report();
      }
    }
    horizontalScrollAnimationFrame=requestAnimationFrame(step);
    return true;
  }

  function setPage(index,duration,behavior,preserveActivationTarget){
    if(!runtimeActive)return false;
    if(selectionPageLock){report();return false;}
    if(preserveActivationTarget!==true){
      activationBoundaryTarget='';activationTargetRevision=-1;
    }
    var m=positionMetrics();
    currentPage=Math.max(0,Math.min(m.pageCount-1,Number(index)||0));
    if(preserveActivationTarget===true&&activationBoundaryTarget){
      activationTargetRevision=layoutRevision;
    }
    if(fixed){positionReaderChrome();commitViewportAnchor();report();return;}
    if(vertical){
      positionReaderChrome();
      window.scrollTo({top:(windowStartPage+currentPage)*Math.max(1,window.innerHeight),left:0,behavior:behavior||'auto'});
      if(behavior!=='smooth')commitViewportAnchor();
    }else if(root){
      var animationMs=Math.max(0,Number(duration)||0);
      var animating=setHorizontalScrollPage(animationMs);
      if(!animating){positionReaderChrome();commitViewportAnchor();}
      if(animating)return;
    }else{
      positionReaderChrome();
    }
    report();
  }

  function setActivationPage(target,index){
    activationBoundaryTarget=target==='end'?'end':target==='start'?'start':'';
    activationTargetRevision=-1;
    var count=computePageCount();
    var boundaryPage=activationBoundaryPage(count);
    setPage(boundaryPage>=0?boundaryPage:index,0,'auto',activationBoundaryTarget!=='');
  }

  function absolutePageForRect(rect){
    if(!rect)return 0;
    if(fixed)return 0;
    if(vertical)return Math.floor((rect.top+window.scrollY)/Math.max(1,window.innerHeight));
    var extent=Math.max(1,window.innerWidth);
    var start=rect.left+window.scrollX-rootTranslation();
    return layoutRtl
      ? Math.max(0,Math.ceil((extent-start-.5)/extent)-1)
      : Math.max(0,Math.floor(Math.max(0,start)/extent));
  }

  function pageForRect(rect){
    computePageCount();
    var absolutePage=absolutePageForRect(rect);
    if(absolutePage<windowStartPage||absolutePage>=windowEndPage)return -1;
    return absolutePage-windowStartPage;
  }

  function isReadAloudRuntimeNode(node){
    var element=node&&node.nodeType===Node.ELEMENT_NODE?node:node&&node.parentElement;
    if(!element)return false;
    if(isReaderChromeNode(element))return true;
    try{
      return !!(element.closest&&element.closest(
        '[data-legado-runtime],#legado-epub-annotation-overlay,#legado-epub-image-overlay'
      ));
    }catch(_){return false;}
  }

  function invalidateReadAloudTextIndex(){readAloudTextIndex=null;}

  function readAloudElementVisible(element,styleCache){
    if(!element||element===root)return true;
    if(isReadAloudRuntimeNode(element))return false;
    var tag=String(element.tagName||'').toUpperCase();
    if(/^(SCRIPT|STYLE|NOSCRIPT|TEMPLATE)$/.test(tag))return false;
    if(element.hidden||String(element.getAttribute&&element.getAttribute('aria-hidden')||'').toLowerCase()==='true')return false;
    try{
      var style=styleFor(element,styleCache);
      return style.display!=='none'&&style.visibility!=='hidden'&&Number(style.opacity||1)>0;
    }catch(_){return true;}
  }

  function readAloudBlockElement(element,styleCache){
    var tag=String(element&&element.tagName||'').toUpperCase();
    if(/^(ADDRESS|ARTICLE|ASIDE|BLOCKQUOTE|CAPTION|DD|DIV|DL|DT|FIELDSET|FIGCAPTION|FIGURE|FOOTER|FORM|H[1-6]|HEADER|HR|LI|MAIN|NAV|OL|P|PRE|SECTION|TABLE|TBODY|TD|TFOOT|TH|THEAD|TR|UL)$/.test(tag))return true;
    try{
      var display=String(styleFor(element,styleCache).display||'').toLowerCase();
      return display==='block'||display==='list-item'||display==='table-cell'||
        display==='table-row'||display==='flex'||display==='grid';
    }catch(_){return false;}
  }

  function buildReadAloudTextIndex(){
    var parts=[];
    var segments=[];
    var length=0;
    var pendingSpace=false;
    var styleCache=window.WeakMap?new WeakMap():null;
    function appendPiece(value,node,nodeStart){
      if(!value)return;
      segments.push({start:length,end:length+value.length,node:node,nodeStart:nodeStart});
      parts.push(value);length+=value.length;
    }
    function appendText(node){
      var value=String(node.nodeValue||'');
      var runStart=-1;
      function flush(end){
        if(runStart<0)return;
        if(pendingSpace&&length>0)appendPiece(' ',node,runStart);
        pendingSpace=false;
        appendPiece(value.slice(runStart,end),node,runStart);
        runStart=-1;
      }
      for(var i=0;i<value.length;i++){
        var character=value.charAt(i);
        if(character==='\u200b'||character==='\ufeff'){
          flush(i);continue;
        }
        if(/[\s\u00a0]/.test(character)){
          flush(i);pendingSpace=true;
        }else if(runStart<0){runStart=i;}
      }
      flush(value.length);
    }
    function visit(node){
      if(!node)return;
      if(node.nodeType===Node.TEXT_NODE){appendText(node);return;}
      if(node.nodeType!==Node.ELEMENT_NODE)return;
      var element=node;
      if(!readAloudElementVisible(element,styleCache))return;
      var tag=String(element.tagName||'').toUpperCase();
      if(tag==='BR'){pendingSpace=true;return;}
      var block=element!==root&&readAloudBlockElement(element,styleCache);
      if(block)pendingSpace=true;
      for(var child=element.firstChild;child;child=child.nextSibling)visit(child);
      if(block)pendingSpace=true;
    }
    visit(root);
    return {text:parts.join(''),segments:segments};
  }

  function currentReadAloudTextIndex(){
    if(!readAloudTextIndex)readAloudTextIndex=buildReadAloudTextIndex();
    return readAloudTextIndex;
  }

  function normalizeReadAloudCue(value,rawOffset){
    value=String(value||'');
    var pieces=[];
    var rawToNormalized=[];
    var length=0;
    var pendingSpace=false;
    var pendingOffsets=[];
    function emitPendingSpace(){
      if(!pendingSpace)return;
      var target=length;
      if(length>0){pieces.push(' ');length++;}
      for(var j=0;j<pendingOffsets.length;j++)rawToNormalized[pendingOffsets[j]]=target;
      pendingSpace=false;pendingOffsets=[];
    }
    for(var i=0;i<value.length;i++){
      var character=value.charAt(i);
      if(character==='\u200b'||character==='\ufeff'){
        rawToNormalized[i]=Math.max(0,length-1);continue;
      }
      if(/[\s\u00a0]/.test(character)){
        pendingSpace=true;pendingOffsets.push(i);continue;
      }
      emitPendingSpace();
      rawToNormalized[i]=length;pieces.push(character);length++;
    }
    var text=pieces.join('').replace(/^ +| +$/g,'');
    var offsetIndex=Math.max(0,Math.min(Math.max(0,value.length-1),Number(rawOffset)||0));
    var offset=Number(rawToNormalized[offsetIndex]);
    if(!isFinite(offset))offset=Math.max(0,text.length-1);
    return {text:text,offset:Math.max(0,Math.min(Math.max(0,text.length-1),offset))};
  }

  function nearestReadAloudOccurrence(text,cue,targetStart){
    var before=text.lastIndexOf(cue,Math.max(0,Math.floor(targetStart)));
    var after=text.indexOf(cue,Math.max(0,Math.ceil(targetStart)));
    if(before<0)return after;
    if(after<0)return before;
    return Math.abs(before-targetStart)<=Math.abs(after-targetStart)?before:after;
  }

  function readAloudDomPosition(index,position){
    var segments=index.segments;
    var low=0,high=segments.length-1;
    while(low<=high){
      var middle=(low+high)>>1;
      var segment=segments[middle];
      if(position<segment.start){high=middle-1;continue;}
      if(position>=segment.end){low=middle+1;continue;}
      var nodeLength=String(segment.node&&segment.node.nodeValue||'').length;
      if(nodeLength<1)return null;
      return {
        node:segment.node,
        offset:Math.max(0,Math.min(nodeLength-1,segment.nodeStart+position-segment.start))
      };
    }
    return null;
  }

  function locateReadAloud(cueText,cueOffset,approximateProgress){
    if(selectionPageLock)return {accepted:false,reason:'selection'};
    var cue=normalizeReadAloudCue(cueText,cueOffset);
    if(!cue.text)return {accepted:false,reason:'empty-cue'};
    var index=currentReadAloudTextIndex();
    if(!index.text)return {accepted:false,reason:'empty-document'};
    var progress=Math.max(0,Math.min(1,Number(approximateProgress)||0));
    var target=Math.round(progress*Math.max(0,index.text.length-1));
    var occurrence=nearestReadAloudOccurrence(index.text,cue.text,target-cue.offset);
    if(occurrence<0)return {accepted:false,reason:'cue-not-found'};
    var position=readAloudDomPosition(index,occurrence+cue.offset);
    if(!position)return {accepted:false,reason:'position-not-found'};
    var range=document.createRange();
    var rect=null;
    try{
      range.setStart(position.node,position.offset);
      range.setEnd(position.node,Math.min(String(position.node.nodeValue||'').length,position.offset+1));
      var rects=range.getClientRects();
      for(var i=0;i<rects.length;i++){
        if(rects[i].width>.5&&rects[i].height>.5){rect=rects[i];break;}
      }
      if(!rect)rect=range.getBoundingClientRect();
    }catch(_){return {accepted:false,reason:'range-failed'};}
    finally{if(range.detach)range.detach();}
    if(!rect||rect.width<=.5||rect.height<=.5)return {accepted:false,reason:'empty-range'};
    var page=pageForRect(rect);
    if(page<0)return {accepted:false,reason:'outside-chapter'};
    return {accepted:true,pageIndex:page,changed:page!==currentPage};
  }

  function selection(){
    clearTimeout(window.__legadoSelectionTimer);
    window.__legadoSelectionTimer=setTimeout(function(){
      if(!runtimeActive)return;
      var s=window.getSelection();var text=s?String(s.toString()):'';var rects=[];
      if(!text&&!selectionPageLock)return;
      if(text&&s&&s.rangeCount){Array.prototype.forEach.call(s.getRangeAt(0).getClientRects(),function(r){
        if(rects.length<512&&r.width>0&&r.height>0)rects.push({left:r.left,top:r.top,right:r.right,bottom:r.bottom});
      });if(!rects.length){var fallback=s.getRangeAt(0).getBoundingClientRect();
        if(fallback.width>0&&fallback.height>0)rects.push({left:fallback.left,top:fallback.top,right:fallback.right,bottom:fallback.bottom});
      }}
       var nextSelectionPageLock=!!(text&&rects.length);
       var selectionReleased=selectionPageLock&&!nextSelectionPageLock;
       selectionPageLock=nextSelectionPageLock;
       if(selectionReleased&&layoutRefreshDeferredBySelection)scheduleLayoutRefresh(true);
       window.__LEGADO_BRIDGE__.onSelection(activeToken,JSON.stringify({
        text:text,
        rects:rects,
        viewportWidth:Math.max(1,window.innerWidth),
        viewportHeight:Math.max(1,window.innerHeight)
      }));
    },50);
  }

  function clearSelection(){
    clearTimeout(window.__legadoSelectionTimer);
    var s=window.getSelection&&window.getSelection();
    var hadSelection=selectionPageLock||!!(s&&!s.isCollapsed);
    selectionPageLock=false;
    if(s&&s.rangeCount)s.removeAllRanges();
    if(!hadSelection&&!layoutRefreshDeferredBySelection)return false;
    if(hadSelection)window.__LEGADO_BRIDGE__.onSelection(activeToken,JSON.stringify({
      text:'',rects:[],viewportWidth:Math.max(1,window.innerWidth),viewportHeight:Math.max(1,window.innerHeight)
    }));
    if(layoutRefreshDeferredBySelection)scheduleLayoutRefresh(true);
    report();
    return hadSelection;
  }

  var lastFragmentPage=-1;
  var textReaderBlocks=null;
  var textReaderPositionCache=null;
  var textReaderIndexRevision=-1;
  var textReaderTextCache=null;
  var textReaderTextFallback=[];
  var textReaderMutationObserver=null;
  function invalidateOrdinaryTextIndex(){
    textReaderBlocks=null;textReaderPositionCache=null;
    textReaderTextCache=window.WeakMap?new WeakMap():null;
    textReaderTextFallback=[];textReaderIndexRevision=layoutRevision;
  }
  function ordinaryTextMutations(mutations){
    for(var i=0;i<mutations.length;i++){
      if(isReaderChromeMutation(mutations[i])||runtimeMediaControlMutation(mutations[i]))continue;
      invalidateOrdinaryTextIndex();return;
    }
  }
  function refreshOrdinaryTextIndex(){
    if(!textReaderMutationObserver&&window.MutationObserver&&root.hasAttribute('data-legado-text-reader')){
      textReaderMutationObserver=new MutationObserver(ordinaryTextMutations);
      textReaderMutationObserver.observe(root,{subtree:true,childList:true,characterData:true,
        attributes:true,attributeFilter:['data-legado-text-offset','data-reader-text-ignore',
          'data-legado-image-action','data-legado-runtime','id']});
    }
    // A template can split/wrap text and immediately restore an anchor, before
    // MutationObserver's callback or the next layout revision has run.
    if(textReaderMutationObserver)ordinaryTextMutations(textReaderMutationObserver.takeRecords());
    if(textReaderIndexRevision!==layoutRevision)invalidateOrdinaryTextIndex();
  }
  function ordinaryTextExcluded(element){
    if(!element)return false;
    if(/^(SCRIPT|STYLE|NOSCRIPT|TEMPLATE|IMG|SVG|CANVAS|IFRAME|OBJECT|EMBED|VIDEO|AUDIO|BUTTON|INPUT|SELECT|TEXTAREA)$/.test(String(element.tagName||'').toUpperCase()))return true;
    if(isReadAloudRuntimeNode(element))return true;
    return !!(element.closest&&element.closest('[data-reader-text-ignore],[data-legado-image-action]'));
  }
  function ordinaryTextBlocks(){
    refreshOrdinaryTextIndex();
    if(!textReaderBlocks)textReaderBlocks=Array.prototype.slice.call(root.querySelectorAll('[data-legado-text-offset]')).filter(function(block){
      var offset=Number(block.getAttribute('data-legado-text-offset'));
      return isFinite(offset)&&offset>=0&&!ordinaryTextExcluded(block);
    });
    return textReaderBlocks;
  }
  function ordinaryTextIndex(block){
    refreshOrdinaryTextIndex();
    var cached=textReaderTextCache&&textReaderTextCache.get(block);
    if(!textReaderTextCache){
      for(var i=0;i<textReaderTextFallback.length;i++){
        if(textReaderTextFallback[i].block===block){cached=textReaderTextFallback[i].index;break;}
      }
    }
    if(cached)return cached;
    var parts=[],segments=[],length=0;
    if(block&&!ordinaryTextExcluded(block)){
      var walker=document.createTreeWalker(block,NodeFilter.SHOW_ELEMENT|NodeFilter.SHOW_TEXT,{
        acceptNode:function(node){
          if(node.nodeType===Node.ELEMENT_NODE)return ordinaryTextExcluded(node)?NodeFilter.FILTER_REJECT:NodeFilter.FILTER_SKIP;
          return node.nodeValue?NodeFilter.FILTER_ACCEPT:NodeFilter.FILTER_SKIP;
        }
      });
      var node;
      while((node=walker.nextNode())){
        var value=String(node.nodeValue||'');
        segments.push({node:node,start:length,end:length+value.length});
        parts.push(value);length+=value.length;
      }
    }
    // Keep raw UTF-16 units and whitespace: these offsets index the same plainText
    // used by native progress, search and bookmarks. Images/UI add no characters.
    var index={text:parts.join(''),segments:segments};
    if(textReaderTextCache&&block)textReaderTextCache.set(block,index);
    else textReaderTextFallback.push({block:block,index:index});
    return index;
  }
  function ordinaryTextOffset(index,offset){
    var text=index.text;
    var bounded=Math.max(0,Math.min(Math.max(0,text.length-1),Math.floor(Number(offset)||0)));
    var unit=text.charCodeAt(bounded);
    if(bounded>0&&unit>=0xDC00&&unit<=0xDFFF){
      var previous=text.charCodeAt(bounded-1);
      if(previous>=0xD800&&previous<=0xDBFF)bounded--;
    }
    return bounded;
  }
  function ordinaryTextPoint(index,offset,endBoundary){
    var segments=index.segments,low=0,high=segments.length-1,found=high;
    if(found<0)return null;
    while(low<=high){
      var mid=(low+high)>>1;
      if(segments[mid].end>offset||(endBoundary&&segments[mid].end===offset)){found=mid;high=mid-1;}
      else low=mid+1;
    }
    var segment=segments[found];
    return {node:segment.node,offset:Math.max(0,Math.min(segment.end-segment.start,offset-segment.start))};
  }
  function ordinaryTextRect(block,offset){
    if(!block||!root.contains(block))return null;
    var index=ordinaryTextIndex(block);
    if(!index.text.length)return fragmentRect(block);
    var start=ordinaryTextOffset(index,offset),end=start+1;
    var first=index.text.charCodeAt(start),second=index.text.charCodeAt(end);
    if(first>=0xD800&&first<=0xDBFF&&second>=0xDC00&&second<=0xDFFF)end++;
    var from=ordinaryTextPoint(index,start,false),to=ordinaryTextPoint(index,end,true);
    if(!from||!to)return null;
    var range=document.createRange();
    try{
      range.setStart(from.node,from.offset);range.setEnd(to.node,to.offset);
      var rects=range.getClientRects();
      for(var i=0;i<rects.length;i++){
        if(rects[i].width>.5&&rects[i].height>.5)return rects[i];
      }
      return range.getBoundingClientRect();
    }catch(_){return null;}
    finally{if(range.detach)range.detach();}
  }
  // Inline images and template spans split paragraphs into multiple TextNodes.
  // Index nodes once, then binary-search pages without measuring every glyph.
  function textReaderPosition(visiblePage){
    var blocks=ordinaryTextBlocks();
    if(textReaderPositionCache&&textReaderPositionCache.page===visiblePage&&textReaderPositionCache.revision===layoutRevision)return textReaderPositionCache.offset;
    if(!blocks.length)return 0;
    var low=0,high=blocks.length-1,found=high;
    while(low<=high){
      var mid=(low+high)>>1,block=blocks[mid];
      var rect=ordinaryTextRect(block,Math.max(0,ordinaryTextIndex(block).text.length-1));
      var page=rect?pageForRect(rect):-1;
      if(page>=visiblePage){found=mid;high=mid-1;}else low=mid+1;
    }
    var target=blocks[found],index=ordinaryTextIndex(target),start=0,end=Math.max(0,index.text.length-1);
    var character=ordinaryTextOffset(index,end);
    while(start<=end){
      var middle=(start+end)>>1,glyph=ordinaryTextOffset(index,middle);
      var r=ordinaryTextRect(target,glyph),p=r?pageForRect(r):-1;
      if(p>=visiblePage){character=glyph;end=glyph-1;}else start=middle+1;
    }
    var result=Math.max(0,(Number(target.getAttribute('data-legado-text-offset'))||0)+character);
    textReaderPositionCache={page:visiblePage,revision:layoutRevision,offset:result};
    return result;
  }
  window.addEventListener('pagehide',function(){
    if(textReaderMutationObserver)textReaderMutationObserver.disconnect();
    textReaderMutationObserver=null;invalidateOrdinaryTextIndex();
  },{passive:true});
  function ordinaryTextTarget(offset){
    var blocks=ordinaryTextBlocks();
    var low=0,high=blocks.length-1,found=0;
    while(low<=high){
      var mid=(low+high)>>1;
      if(Number(blocks[mid].getAttribute('data-legado-text-offset'))<=offset){found=mid;low=mid+1;}else high=mid-1;
    }
    var block=blocks[found];
    return block?ordinaryTextRect(block,offset-Number(block.getAttribute('data-legado-text-offset'))):null;
  }
  function goToFragment(id,notifyNative){
    if(root.hasAttribute('data-legado-text-reader')&&/^__legado_text_\d+$/.test(String(id))){
      var textRect=ordinaryTextTarget(Number(String(id).substring(14)));
      var textPage=textRect?pageForRect(textRect):-1;
      if(textPage<0)return false;
      lastFragmentPage=textPage;setPage(textPage,0,'auto');
      if(notifyNative!==false)window.__LEGADO_BRIDGE__.onHighlightPage(activeToken,textPage);
      return true;
    }
    var target=fragmentTarget(id);
    if(!target)return false;
    if(!targetInsideFragmentWindow(target,fragmentWindowTargets()))return false;
    var page=pageForRect(fragmentRect(target));
    if(page<0)return false;
    lastFragmentPage=page;
    setPage(page,0,'auto');
    if(notifyNative!==false)window.__LEGADO_BRIDGE__.onHighlightPage(activeToken,page);
    return true;
  }

  function imageOverlay(url){
    var overlay=document.getElementById('legado-epub-image-overlay');
    if(!overlay){
      overlay=document.createElement('div');overlay.id='legado-epub-image-overlay';
      var image=document.createElement('img');overlay.appendChild(image);
      var scale=1,startDistance=0,startX=0,startY=0;
      function applyScale(){image.style.setProperty('transform','scale('+Math.max(1,Math.min(4,scale))+')','important');}
      function close(){overlay.classList.remove('legado-visible');image.removeAttribute('src');scale=1;applyScale();}
      overlay.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();close();},true);
      overlay.addEventListener('touchstart',function(e){
        if(e.touches.length===2){
          var dx=e.touches[0].clientX-e.touches[1].clientX,dy=e.touches[0].clientY-e.touches[1].clientY;
          startDistance=Math.sqrt(dx*dx+dy*dy);
        }else if(e.touches.length===1){startX=e.touches[0].clientX;startY=e.touches[0].clientY;}
      },{passive:true});
      overlay.addEventListener('touchmove',function(e){
        if(e.touches.length===2&&startDistance>0){
          var dx=e.touches[0].clientX-e.touches[1].clientX,dy=e.touches[0].clientY-e.touches[1].clientY;
          var distance=Math.sqrt(dx*dx+dy*dy);scale=Math.max(1,Math.min(4,scale*distance/startDistance));
          startDistance=distance;applyScale();e.preventDefault();
        }
      },{passive:false});
      overlay.addEventListener('touchend',function(e){
        startDistance=0;
        if(e.changedTouches.length===1&&scale<=1.02){
          var dx=e.changedTouches[0].clientX-startX,dy=e.changedTouches[0].clientY-startY;
          if(Math.abs(dx)<12&&Math.abs(dy)<12)close();
        }
      },{passive:true});
      overlay.addEventListener('touchcancel',function(){
        startDistance=0;
      },{passive:true});
      document.documentElement.appendChild(overlay);
    }
    var image=overlay.querySelector('img');if(image&&url){image.src=url;image.alt='';overlay.classList.add('legado-visible');}
  }

  var IMAGE_LONG_PRESS_MS=500;
  var IMAGE_LONG_PRESS_MOVE_PX=12;
  var imageLongPress=null;
  var suppressImageClickUntil=0;
  var sourceImageSequence=0;
  var lastSourceImageTap=null;
  var lastSourceImageActionAt=0;
  function setTextImageMode(mode){
    mode=String(mode);
    var next=/^[0-4]$/.test(mode)?mode:'epub';
    if(next!==textImageMode){lastSourceImageTap=null;lastSourceImageActionAt=0;}
    textImageMode=next;
  }
  function isTextReaderImage(image){
    return textImageMode!=='epub'&&root.hasAttribute('data-legado-text-reader')&&image&&
      image.hasAttribute('data-legado-image-id')&&root.contains(image);
  }
  function sourceImageClick(image,event){
    if(!isTextReaderImage(image))return false;
    var anchor=image.closest('a[data-legado-image-action]');
    if(!anchor&&textImageMode!=='1')return false;
    event.preventDefault();event.stopPropagation();
    if(textImageMode==='3'||!event.isTrusted)return true;
    if(textImageMode==='1'){
      var src=image.currentSrc||image.src||'';
      if(src){imageOverlay(src);window.__LEGADO_BRIDGE__.onImage(activeToken,src);}
      return true;
    }
    var id=anchor.getAttribute('data-legado-image-action');
    if(!/^image-\d+$/.test(id||''))return true;
    var now=Date.now(),page=positionMetrics().pageIndex;
    if(textImageMode==='4'){
      var previous=lastSourceImageTap;
      lastSourceImageTap={id:id,at:now,page:page,revision:layoutRevision};
      if(!previous||previous.id!==id||now-previous.at>300||previous.page!==page||previous.revision!==layoutRevision)return true;
      lastSourceImageTap=null;
    }
    if(now-lastSourceImageActionAt<300)return true;
    lastSourceImageActionAt=now;
    var bridge=window.__LEGADO_BRIDGE__;
    if(bridge&&typeof bridge.onSourceImage==='function'){
      bridge.onSourceImage(activeToken,page,layoutRevision,id,++sourceImageSequence);
    }
    return true;
  }
  function imageTarget(target){
    var image=target&&target.closest?target.closest('img'):null;
    return image&&(image.closest('#legado-epub-image-overlay')||inlineImageFootnoteTarget(image)||
      runtimeMediaControlNode(image))?null:image;
  }
  function textReaderImageTarget(target){
    var element=target&&target.nodeType===Node.ELEMENT_NODE?target:target&&target.parentElement;
    if(!(element&&element.closest))return null;
    var image=imageTarget(element);
    if(isTextReaderImage(image))return image;
    var anchor=element.closest('a[data-legado-image-action]');
    if(!anchor)return null;
    image=anchor.querySelector('img[data-legado-image-id]');
    // Generated anchor spacing is part of the same source action. Its internal
    // placeholder href must not escape through the ordinary external-link path.
    return isTextReaderImage(image)&&anchor.getAttribute('data-legado-image-action')===
      image.getAttribute('data-legado-image-id')?image:null;
  }
  function clearImageLongPress(){
    if(imageLongPress&&imageLongPress.timer)clearTimeout(imageLongPress.timer);
    imageLongPress=null;
  }
  function showLongPressedImage(state){
    if(!state||imageLongPress!==state||state.moved||state.opened)return;
    var src=state.image.currentSrc||state.image.src||'';
    if(!src){clearImageLongPress();return;}
    state.opened=true;
    suppressImageClickUntil=Date.now()+700;
    imageOverlay(src);
    window.__LEGADO_BRIDGE__.onImage(activeToken,src);
  }
  document.addEventListener('touchstart',function(e){
    clearImageLongPress();
    if(e.touches.length!==1)return;
    var image=imageTarget(e.target);if(!image)return;
    var touch=e.touches[0];
    var state={image:image,identifier:touch.identifier,x:touch.clientX,y:touch.clientY,moved:false,opened:false,timer:0};
    state.timer=setTimeout(function(){showLongPressedImage(state);},IMAGE_LONG_PRESS_MS);
    imageLongPress=state;
  },{capture:true,passive:true});
  document.addEventListener('touchmove',function(e){
    var state=imageLongPress;if(!state||state.opened)return;
    var touch=findTouch(e.touches,state.identifier);
    if(!touch||Math.abs(touch.clientX-state.x)>=IMAGE_LONG_PRESS_MOVE_PX||
      Math.abs(touch.clientY-state.y)>=IMAGE_LONG_PRESS_MOVE_PX){
      state.moved=true;clearImageLongPress();
    }
  },{capture:true,passive:true});
  document.addEventListener('touchend',function(e){
    var state=imageLongPress;if(!state)return;
    var opened=state.opened;
    clearImageLongPress();
    if(opened){suppressImageClickUntil=Date.now()+700;e.preventDefault();e.stopPropagation();}
  },{capture:true,passive:false});
  document.addEventListener('touchcancel',clearImageLongPress,{capture:true,passive:true});
  document.addEventListener('contextmenu',function(e){
    var inlineNote=inlineImageFootnoteTarget(e.target);
    if(inlineNote){e.preventDefault();e.stopPropagation();showInlineImageFootnote(inlineNote);return;}
    var image=imageTarget(e.target);if(!image)return;
    e.preventDefault();e.stopPropagation();
    if(Date.now()<suppressImageClickUntil)return;
    if(imageLongPress&&imageLongPress.image===image&&imageLongPress.opened){
      suppressImageClickUntil=Date.now()+700;return;
    }
    var src=image.currentSrc||image.src||'';if(!src)return;
    suppressImageClickUntil=Date.now()+700;
    clearImageLongPress();
    imageOverlay(src);
    window.__LEGADO_BRIDGE__.onImage(activeToken,src);
  },true);

  normalizeHorizontalReflow();
  clampHorizontalRootScroll();
  applyFixedScale();
  prepareInteractiveMedia();
  readerChromeConfigKey=readerChromeConfigSignature();
  if(readerChromeSupported())positionReaderChrome();
  if(layoutRtl)detectRtlScrollType();

  // 取词: 命中点的单词, 供阅读取词模式使用
  var WORD_BREAK_PUNCTUATION='.,;:!?\'"()[]{}<>/\\|@#$%^&*+=~`-_';
  function isWordCharCode(code){
    if(code<=32)return false;
    if(code>=0x2000&&code<=0x206f)return false;
    if(code>=0x2e00&&code<=0x2e7f)return false;
    if(code>=0x3000&&code<=0x303f)return false;
    if(code>=0xfe10&&code<=0xfe1f)return false;
    if(code>=0xfe30&&code<=0xfe4f)return false;
    if(code>=0xff00&&code<=0xff0f)return false;
    if(code>=0xff1a&&code<=0xff20)return false;
    if(code>=0xff3b&&code<=0xff40)return false;
    if(code>=0xff5b&&code<=0xff65)return false;
    return WORD_BREAK_PUNCTUATION.indexOf(String.fromCharCode(code))<0;
  }
  function isCjkCode(code){
    return (code>=0x3040&&code<=0x30ff)||(code>=0x3400&&code<=0x4dbf)||
      (code>=0x4e00&&code<=0x9fff)||(code>=0xac00&&code<=0xd7af)||
      (code>=0xf900&&code<=0xfaff);
  }
  function wordLocaleForText(text){
    if(/[\u3040-\u30ff]/.test(text))return 'ja';
    if(/[\uac00-\ud7af]/.test(text))return 'ko';
    if(/[\u4e00-\u9fff]/.test(text))return 'zh';
    return 'en';
  }
  function expandWordChars(text,index){
    if(index<0||index>=text.length)return '';
    if(!isWordCharCode(text.charCodeAt(index)))return '';
    if(isCjkCode(text.charCodeAt(index)))return text.charAt(index);
    var start=index,end=index+1;
    while(start>0&&isWordCharCode(text.charCodeAt(start-1)))start--;
    while(end<text.length&&isWordCharCode(text.charCodeAt(end)))end++;
    return text.slice(start,end);
  }
  function segmentWordAt(text,index){
    if(index<0||index>=text.length)return '';
    try{
      if(window.Intl&&window.Intl.Segmenter){
        var segmenter=new Intl.Segmenter(wordLocaleForText(text),{granularity:'word'});
        var iterator=segmenter.segment(text);
        if(typeof Symbol!=='undefined'&&iterator[Symbol.iterator]){
          var step=iterator[Symbol.iterator]();
          var item=step.next();
          while(!item.done){
            var part=item.value;
            if(index>=part.index&&index<part.index+part.segment.length){
              return part.isWordLike?part.segment:'';
            }
            item=step.next();
          }
        }
        return '';
      }
    }catch(_){}
    return expandWordChars(text,index);
  }
  function charRectAt(node,index){
    try{
      var length=String(node.nodeValue||'').length;
      if(index<0||index>=length)return null;
      var range=document.createRange();
      range.setStart(node,index);
      range.setEnd(node,index+1);
      var rect=range.getBoundingClientRect();
      if(rect&&rect.width>0)return rect;
    }catch(_){}
    return null;
  }
  // caret 返回的是字符间隙, 点击落在字符右半边时 offset 会指向下一个字符,
  // 用字形矩形与点击位置比对, 取真正被点到的那个字符
  function caretCharIndex(node,offset,x){
    var length=String(node.nodeValue||'').length;
    if(length<=0)return -1;
    var candidates=[];
    if(offset>0)candidates.push(offset-1);
    if(offset<length)candidates.push(offset);
    var best=-1,bestDistance=Infinity;
    for(var i=0;i<candidates.length;i++){
      var index=candidates[i];
      var rect=charRectAt(node,index);
      if(!rect)continue;
      if(x>=rect.left&&x<=rect.right)return index;
      var distance=x<rect.left?rect.left-x:x-rect.right;
      if(distance<bestDistance){bestDistance=distance;best=index;}
    }
    if(best>=0)return best;
    return Math.max(0,Math.min(length-1,offset));
  }
  function wordAtPoint(x,y){
    var node=null,offset=0;
    try{
      if(document.caretPositionFromPoint){
        var position=document.caretPositionFromPoint(x,y);
        node=position&&position.offsetNode;
        offset=position&&position.offset||0;
      }else if(document.caretRangeFromPoint){
        var range=document.caretRangeFromPoint(x,y);
        node=range&&range.startContainer;
        offset=range&&range.startOffset||0;
      }
    }catch(_){}
    if(!node||node.nodeType!==3)return '';
    var text=String(node.nodeValue||'');
    if(!text)return '';
    var index=caretCharIndex(node,offset,x);
    if(index<0)return '';
    var word=segmentWordAt(text,index)||segmentWordAt(text,index-1)||segmentWordAt(text,index+1);
    return word.length>48?word.slice(0,48):word;
  }

  var api={
    token:activeToken,
    setToken:function(value){
      closeAnnotation(false);
      var next=Number(value)||0;
      if(next!==activeToken){
        clearImageLongPress();lastSourceImageTap=null;lastSourceImageActionAt=0;
        sourceImageSequence=0;suppressImageClickUntil=0;
      }
      activeToken=next;api.token=activeToken;
      reportRenderState();
    },
    setTextImageMode:setTextImageMode,
    metrics:metrics,
    report:report,
    replayRuntimeTerminal:replayRuntimeTerminal,
    setPage:setPage,
    setActivationPage:setActivationPage,
    setReaderChrome:setReaderChrome,
    setReaderChromeData:setReaderChromeData,
    locateReadAloud:locateReadAloud,
    clearSelection:clearSelection,
    wordAt:wordAtPoint,
    goToFragment:goToFragment,
    dismissAnnotation:dismissAnnotation,
    annotationVisible:function(){return annotationVisible;},
    lastFragmentPage:function(){return lastFragmentPage;}
  };
  window.__legadoEpub=api;
  document.addEventListener('selectionchange',selection,{passive:true});
  document.addEventListener('touchstart',function(e){
    if(e.touches.length!==1||embeddedTouch)return;
    var interaction=interactiveTarget(e.target);
    if(!interaction)return;
    var touch=e.touches[0];
    embeddedTouch={
      id:++embeddedInteractionSerial,identifier:touch.identifier,
      x:touch.clientX,y:touch.clientY,hard:interaction.hard,
      scrollers:interaction.scrollers,active:false
    };
    if(interaction.hard)reportEmbeddedTouch(true);
  },{capture:true,passive:true});
  document.addEventListener('touchmove',function(e){
    if(!embeddedTouch||embeddedTouch.hard)return;
    var touch=findTouch(e.touches,embeddedTouch.identifier);
    if(!touch)return;
    var dx=touch.clientX-embeddedTouch.x;
    var dy=touch.clientY-embeddedTouch.y;
    if(Math.max(Math.abs(dx),Math.abs(dy))<4)return;
    var consume=embeddedTouch.scrollers.some(function(entry){
      return scrollableForGesture(entry,dx,dy);
    });
    reportEmbeddedTouch(consume);
  },{capture:true,passive:true});
  document.addEventListener('touchend',finishEmbeddedTouch,{capture:true,passive:true});
  document.addEventListener('touchcancel',finishEmbeddedTouch,{capture:true,passive:true});
  window.addEventListener('blur',function(){finishEmbeddedTouch(null);},{passive:true});
  window.addEventListener('pagehide',function(){
    finishEmbeddedTouch(null);
    runtimeActive=false;
    clearTimeout(timer);clearTimeout(layoutRefreshTimer);
    cancelAnimationFrame(layoutRefreshFrame);cancelAnimationFrame(renderStateFrame);
    cancelAnimationFrame(horizontalScrollAnimationFrame);horizontalScrollAnimationFrame=0;
    clearTimeout(resourceSettlementTimer);
    layoutRefreshFrame=0;layoutRefreshTimer=0;renderStateFrame=0;
    deferredLayoutFlushCallback=null;
  },{passive:true});
  document.addEventListener('visibilitychange',function(){
    if(document.hidden)finishEmbeddedTouch(null);
  },{passive:true});
  document.addEventListener('click',function(e){
    var inlineNote=inlineImageFootnoteTarget(e.target);
    if(inlineNote){e.preventDefault();e.stopPropagation();showInlineImageFootnote(inlineNote);return;}
    var image=textReaderImageTarget(e.target)||imageTarget(e.target);
    if(image&&Date.now()<suppressImageClickUntil){e.preventDefault();e.stopPropagation();return;}
    if(image&&sourceImageClick(image,e))return;
    var a=e.target&&e.target.closest?e.target.closest('a[href]'):null;
    if(a){
      e.preventDefault();e.stopPropagation();
      if(isFootnoteLink(a)){
        if(!showAnnotation(a))window.__LEGADO_BRIDGE__.onFootnote(activeToken,a.href);
      }
      else window.__LEGADO_BRIDGE__.onLink(activeToken,a.href);
      return;
    }
  },true);
  window.addEventListener('resize',function(){positionReaderChrome();scheduleLayoutRefresh(true);},{passive:true});
  // Do not write scrollTop from the scroll callback. A root scroll event may be
  // generated by a legitimate vertical gesture inside a reflowable document;
  // fighting it every frame makes the WebView appear locked. Root normalization
  // is performed only at layout/page positioning boundaries above.
  window.addEventListener('scroll',function(){updateReaderChromeForDisplayedPage();report();},{passive:true});
  function layoutResizeGeometry(){
    var htmlRect=document.documentElement.getBoundingClientRect();
    var rootRect=root===document.documentElement?htmlRect:root.getBoundingClientRect();
    return [htmlRect.width,htmlRect.height,rootRect.width,rootRect.height].join('|');
  }
  if(window.ResizeObserver){
    var resizeObserver=new ResizeObserver(function(){
      var geometry=layoutResizeGeometry();
      // Column fitting changes the reader's own box. Its final size was already
      // measured by that refresh; only a later size change needs another pass.
      if(geometry===lastLayoutResizeGeometry)return;
      lastLayoutResizeGeometry=geometry;
      scheduleLayoutRefresh();
    });
    resizeObserver.observe(document.documentElement);
    if(root&&root!==document.documentElement)resizeObserver.observe(root);
  }
  function readerNeutralRootStyle(cssText){
    var scratch=document.createElement('div');scratch.style.cssText=cssText||'';
    ['--legado-fixed-scale','--legado-line-grid','left','top'].forEach(function(name){
      scratch.style.removeProperty(name);
    });
    return scratch.style.cssText;
  }
  function readerNeutralParagraphStyle(cssText){
    var scratch=document.createElement('div');scratch.style.cssText=cssText||'';
    ['--legado-page-gap','--legado-highlight-line-grid'].forEach(function(name){scratch.style.removeProperty(name);});
    return scratch.style.cssText;
  }
  if(window.MutationObserver){readerLayoutMutationObserver=new MutationObserver(function(mutations){
    for(var i=0;i<mutations.length;i++){
      var mutation=mutations[i];
      if(isReaderChromeMutation(mutation))continue;
      if(runtimeMediaControlMutation(mutation))continue;
      if(mutation.type==='attributes'&&mutation.target===root&&mutation.attributeName==='style'&&
        readerNeutralRootStyle(mutation.oldValue)===readerNeutralRootStyle(root.getAttribute('style')))continue;
      if(mutation.type==='attributes'&&mutation.attributeName==='style'&&mutation.target!==root&&
        readerNeutralParagraphStyle(mutation.oldValue)===readerNeutralParagraphStyle(mutation.target.getAttribute('style')))continue;
      if(mutation.type==='childList'||mutation.type==='characterData'||
        (mutation.type==='attributes'&&/^(class|style|hidden|src|srcset|href|poster|data|width|height|sizes)$/.test(mutation.attributeName||''))){
        if(!isReadAloudRuntimeNode(mutation.target))invalidateReadAloudTextIndex();
        scheduleLayoutRefresh(false);break;
      }
    }
  });readerLayoutMutationObserver.observe(root,readerLayoutMutationOptions);}
  var layoutResourceTags=/^(IMG|IMAGE|VIDEO|AUDIO|SOURCE|TRACK|LINK|STYLE|OBJECT|EMBED|IFRAME|SVG|CANVAS)$/;
  var preloadCriticalResourceTags=/^(IMG|IMAGE|OBJECT|EMBED|IFRAME)$/;
  function markResourceFailure(){resourcesFailed=true;}
  function layoutResourceEvent(event){
    var target=event&&event.target;
    var tag=String(target&&target.tagName||'');
    if(!target||runtimeMediaControlNode(target)||runtimeMediaElement(target)||!layoutResourceTags.test(tag))return;
    if(event.type==='error'&&!(target.hasAttribute&&target.hasAttribute('data-legado-image-resource'))&&(
      preloadCriticalResourceTags.test(tag)||
      (tag==='LINK'&&/(?:^|\\s)stylesheet(?:\\s|$)/i.test(String(target.rel||'')))
    ))markResourceFailure();
    scheduleLayoutRefresh(true);
  }
  document.addEventListener('load',layoutResourceEvent,true);
  document.addEventListener('error',layoutResourceEvent,true);
  document.addEventListener('loadedmetadata',layoutResourceEvent,true);
  document.addEventListener('durationchange',layoutResourceEvent,true);
  document.addEventListener('loadeddata',layoutResourceEvent,true);
  if(document.fonts&&document.fonts.addEventListener){
    document.fonts.addEventListener('loadingdone',function(){scheduleLayoutRefresh(true);});
    document.fonts.addEventListener('loadingerror',function(){markResourceFailure();scheduleLayoutRefresh(true);});
  }
  Array.prototype.forEach.call(document.querySelectorAll('img[loading],iframe[loading]'),function(resource){
    if(String(resource.getAttribute('loading')||'').toLowerCase()==='lazy'){
      resource.setAttribute('loading','eager');
    }
  });
  function decodeImage(image){
    return image&&image.decode?image.decode().catch(function(){}):Promise.resolve();
  }
  function imageReady(img){
    if(img&&img.hasAttribute('data-legado-image-resource'))return Promise.resolve();
    var url=String(img&&(img.currentSrc||img.getAttribute('src')||img.getAttribute('srcset'))||'').trim();
    if(!url)return Promise.resolve();
    function loaded(){
      return decodeImage(img).then(function(){
        if(!imageHasPixels(img)&&!img.hasAttribute('data-legado-image-resource'))markResourceFailure();
      });
    }
    if(img.complete)return loaded();
    return new Promise(function(resolve){
      function done(){loaded().then(resolve,resolve);}
      img.addEventListener('load',done,{once:true});
      img.addEventListener('error',function(){
        if(!img.hasAttribute('data-legado-image-resource'))markResourceFailure();
        resolve();
      },{once:true});
    });
  }
  function startTextReaderImages(){
    if(!root.hasAttribute('data-legado-text-reader'))return;
    var images=Array.prototype.slice.call(root.querySelectorAll('img[data-legado-image-resource]'));
    if(!images.length)return;
    sourceImagesPending=images.length;
    var stopped=false,index=0,active=0,pumping=false;
    var requests=[],timers=[];
    var failedImage="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='32' height='24' viewBox='0 0 32 24'%3E%3Crect x='1' y='1' width='30' height='22' rx='4' fill='%23888888' fill-opacity='.18'/%3E%3Cpath d='m11 7 10 10m0-10L11 17' stroke='%23888888' stroke-width='2'/%3E%3C/svg%3E";
    function forget(list,value){var at=list.indexOf(value);if(at>=0)list.splice(at,1);}
    function later(callback,delay){
      var timer=setTimeout(function(){forget(timers,timer);if(!stopped)callback();},delay);
      timers.push(timer);
      return timer;
    }
    function fail(img){
      if(stopped||!root.contains(img))return;
      scheduleLayoutRefresh(true);
      img.setAttribute('data-legado-image-state','failed');
      img.setAttribute('aria-label','图片加载失败');
      img.setAttribute('title','图片加载失败，刷新章节可重试');
      img.src=failedImage;
    }
    function pump(){
      if(pumping)return;pumping=true;
      while(!stopped&&active<4&&index<images.length){active++;load(images[index++]);}
      pumping=false;
    }
    function load(img){
      var url=String(img.getAttribute('data-legado-image-resource')||'');
      var loadingPlaceholder=img.getAttribute('src')||failedImage;
      var parsed=document.createElement('a');parsed.href=url;
      var origin=document.createElement('a');origin.href=document.baseURI||location.href;
      var started=Date.now(),loadingSince=0,attempt=0,reloads=0,finished=false;
      function finish(){
        if(finished)return;finished=true;
        sourceImagesPending=Math.max(0,sourceImagesPending-1);
        active--;pump();
      }
      function rejected(){fail(img);finish();}
      // Only app-generated same-origin endpoints are eligible; EPUB publisher
      // images never acquire this ordinary-book resource path or source scripts.
      if(parsed.protocol!==origin.protocol||parsed.host!==origin.host||
        !/^\/text-image\//.test(parsed.pathname)){rejected();return;}
      function show(resource){
        if(stopped||finished||!root.contains(img)){finish();return;}
        var settled=false,loadTimer;
        function done(ok){
          if(settled)return;settled=true;
          clearTimeout(loadTimer);forget(timers,loadTimer);
          img.removeEventListener('load',loaded);img.removeEventListener('error',error);
          if(!stopped){
            scheduleLayoutRefresh(true);
            if(ok){img.setAttribute('data-legado-image-state','ready');img.removeAttribute('aria-label');}
            else if(reloads++<1){
              // A completed entry can expire between its status and body request.
              // Reacquire once; repeated decode/network failures remain visible.
              img.src=loadingPlaceholder;
              later(poll,0);return;
            }else fail(img);
          }
          finish();
        }
        function loaded(){decodeImage(img).then(function(){done(imageHasPixels(img));},function(){done(false);});}
        function error(){done(false);}
        img.addEventListener('load',loaded);img.addEventListener('error',error);
        scheduleLayoutRefresh(true);
        var bubble=resource.bubble===true;
        img.classList.toggle('legado-text-bubble',bubble);
        var scale=bubble?Number(resource.scale):1;
        // Only native-rendered bubbles use the package scale. Reset on retries
        // so a default-size or ordinary image never retains an earlier scale.
        img.style.fontSize=scale>=.5&&scale<=1.5&&scale!==1?(scale*100)+'%':'';
        img.src=url+(reloads?'?retry='+reloads:'');
        loadTimer=later(function(){done(false);},6000);
      }
      function poll(){
        if(stopped||finished||!root.contains(img)){finish();return;}
        if(Date.now()-started>120000||(loadingSince&&Date.now()-loadingSince>30000)){rejected();return;}
        var xhr=new XMLHttpRequest();requests.push(xhr);
        var settled=false;
        function complete(value){
          if(settled)return;settled=true;forget(requests,xhr);
          if(stopped||finished)return;
          if(value&&value.state==='ready'){show(value);return;}
          if(value&&value.state==='pending'){
            if(value.queued===true)loadingSince=0;
            else if(!loadingSince)loadingSince=Date.now();
            later(poll,Math.min(750,100+(attempt++)*100));return;
          }
          rejected();
        }
        xhr.open('GET',url+'/state',true);xhr.timeout=3000;
        xhr.onload=function(){
          var value=null;
          if(xhr.status===200){try{value=JSON.parse(xhr.responseText);}catch(_){}}
          complete(value);
        };
        xhr.onerror=function(){complete(null);};xhr.ontimeout=function(){complete(null);};
        xhr.onabort=function(){complete(null);};
        try{xhr.send();}catch(_){complete(null);}
      }
      poll();
    }
    window.addEventListener('pagehide',function(){
      stopped=true;
      timers.splice(0).forEach(clearTimeout);
      requests.splice(0).forEach(function(xhr){xhr.abort();});
    },{once:true});
    pump();
  }
  function trackedImageReady(url,statuses){
    if(!url)return Promise.resolve();
    return new Promise(function(resolve){
      var settled=false;
      var image=new Image();
      function done(value){
        if(settled)return;
        settled=true;statuses[url]=value;
        if(!value)markResourceFailure();
        resolve();
      }
      image.onload=function(){
        decodeImage(image).then(function(){done(imageHasPixels(image));},function(){done(false);});
      };
      image.onerror=function(){done(false);};
      image.src=url;
      if(image.complete){
        decodeImage(image).then(function(){done(imageHasPixels(image));},function(){done(false);});
      }
    });
  }
  function stylesheetReady(link){
    if(!link||!link.href||link.sheet)return Promise.resolve();
    return new Promise(function(resolve){
      var settled=false;
      function done(failed){
        if(settled)return;
        settled=true;
        if(failed)markResourceFailure();
        resolve();
      }
      link.addEventListener('load',function(){done(false);},{once:true});
      link.addEventListener('error',function(){done(true);},{once:true});
      setTimeout(function(){done(!link.sheet);},1600);
    });
  }
  function fontsReady(){
    if(!(document.fonts&&document.fonts.ready))return Promise.resolve();
    return Promise.resolve(document.fonts.ready).then(function(){
      if(document.fonts.forEach)document.fonts.forEach(function(face){
        if(String(face.status||'').toLowerCase()==='error')markResourceFailure();
      });
    },function(){markResourceFailure();});
  }
  function readerFontReady(){
    if(!readerFontConfigured)return Promise.resolve();
    if(!(document.fonts&&document.fonts.load)){
      return Promise.reject(new Error('Custom EPUB reader font API is unavailable'));
    }
    return new Promise(function(resolve,reject){
      var settled=false;
      var timeout=setTimeout(function(){finish(new Error('Custom EPUB reader font timed out'));},3000);
      function finish(error){
        if(settled)return;
        settled=true;clearTimeout(timeout);
        if(error)reject(error);else resolve();
      }
      try{
        Promise.resolve(document.fonts.load("1em 'legado-reader-font'")).then(function(faces){
          var loaded=faces&&faces.length>0&&Array.prototype.every.call(faces,function(face){
            return String(face&&face.status||'').toLowerCase()==='loaded';
          });
          var checked=!document.fonts.check||document.fonts.check("1em 'legado-reader-font'");
          finish(loaded&&checked?null:new Error('Custom EPUB reader font was not loaded'));
        },function(){finish(new Error('Custom EPUB reader font failed to load'));});
      }catch(_){finish(new Error('Custom EPUB reader font failed to load'));}
    });
  }
  function backgroundImagesReady(){
    return new Promise(function(resolve){
      var urls=[];
      var seen=Object.create(null);
      var nodes=[root].concat(Array.prototype.slice.call(root.querySelectorAll('*'),0,4000));
      var index=0;
      var deadline=performance.now()+1500;
      function scanChunk(){
        var frameDeadline=performance.now()+8;
        while(index<nodes.length&&urls.length<48&&performance.now()<frameDeadline){
          var backgroundStyle=window.getComputedStyle(nodes[index++]);
          var value=String(backgroundStyle.backgroundImage||'')+' '+String(backgroundStyle.borderImageSource||'');
          var candidates=backgroundImageUrls(value);
          for(var candidateIndex=0;candidateIndex<candidates.length&&urls.length<48;candidateIndex++){
            var url=candidates[candidateIndex];
            if(url&&!seen[url]){seen[url]=true;urls.push(url);}
          }
        }
        if(index<nodes.length&&urls.length<48&&performance.now()<deadline){
          requestAnimationFrame(scanChunk);return;
        }
        var loads=urls.map(function(url){
          return new Promise(function(done){
            var settled=false;
            var image=new Image();
            function finish(value){
              if(settled)return;
              settled=true;loadedBackgroundImages[url]=value;
              if(!value)markResourceFailure();
              done();
            }
            function loaded(){
              decodeImage(image).then(function(){
                finish(imageHasPixels(image));
              },function(){finish(false);});
            }
            image.onload=loaded;
            image.onerror=function(){finish(false);};
            image.src=url;
            if(image.complete)loaded();
          });
        });
        Promise.all(loads).then(resolve,resolve);
      }
      scanChunk();
    });
  }
  function notifyStable(){
    if(!runtimeActive)return;
    requestAnimationFrame(function(){
      if(!runtimeActive)return;
      // Drain actual late changes without unconditionally rebuilding the entire
      // chapter again. Keep two frame boundaries so resize/resource observers
      // can invalidate the result before a native ready signal is published.
      if(isLayoutPending()){flushLayoutRefresh(notifyStable);return;}
      var revision=visualRevision;
      requestAnimationFrame(function(){
        if(!runtimeActive)return;
        if(isLayoutPending()||revision!==visualRevision){notifyStable();return;}
        stable=true;
        var boundaryPage=activationBoundaryPage(computePageCount());
        if(boundaryPage>=0)setPage(boundaryPage,0,'auto',true);else report();
        settleRuntime('ready','');
      });
    });
  }
  function replayRuntimeTerminal(token){
    if(!runtimeActive)return;
    var nextToken=Number(token)||0;
    if(!runtimeTerminal||nextToken!==activeToken||runtimeTerminalReportedToken===nextToken)return;
    var bridge=window.__LEGADO_BRIDGE__;
    if(!bridge)return;
    if(runtimeTerminal.kind==='ready'&&typeof bridge.onStable==='function'){
      runtimeTerminalReportedToken=nextToken;
      bridge.onStable(nextToken);
    }else if(runtimeTerminal.kind==='reader-font-error'&&typeof bridge.onFontError==='function'){
      runtimeTerminalReportedToken=nextToken;
      bridge.onFontError(nextToken,runtimeTerminal.message);
    }
  }
  function settleRuntime(kind,message){
    if(runtimeTerminal)return;
    runtimeTerminal={kind:String(kind||''),message:String(message||'')};
    replayRuntimeTerminal(activeToken);
  }
  var initialStablePublished=false;
  function publishInitialStable(){
    if(!runtimeActive||initialStablePublished)return;
    initialStablePublished=true;
    markReaderParagraphs();
    resourcesReady=true;
    flushLayoutRefresh(notifyStable);
  }
  // Source images load independently. Their placeholders already have bounds,
  // so a broken/slow source image cannot hold the chapter's ready signal hostage.
  startTextReaderImages();
  var resourceSettlementTimer;
  var resourceSettlementTimeout=new Promise(function(resolve){
    resourceSettlementTimer=setTimeout(function(){markResourceFailure();resolve();},3200);
  });
  var styles=Array.prototype.map.call(
    document.querySelectorAll('link[rel~="stylesheet"]'),stylesheetReady
  );
  var allResourcesReady=Promise.all(styles).then(function(){
    var images=Array.prototype.map.call(document.images||[],imageReady);
    var svgImageUrls=[];
    var svgImageSeen=Object.create(null);
    Array.prototype.forEach.call(document.querySelectorAll('svg image'),function(image){
      var url=svgImageUrl(image);
      if(url&&!svgImageSeen[url]){svgImageSeen[url]=true;svgImageUrls.push(url);}
    });
    var svgImages=svgImageUrls.map(function(url){return trackedImageReady(url,loadedSvgImages);});
    return Promise.all([fontsReady(),Promise.all(images),Promise.all(svgImages),backgroundImagesReady()]);
  }).catch(function(){markResourceFailure();});
  Promise.all([
    readerFontReady(),
    Promise.race([allResourcesReady,resourceSettlementTimeout]).then(function(){clearTimeout(resourceSettlementTimer);})
  ]).then(publishInitialStable,function(error){
    markResourceFailure();
    settleRuntime('reader-font-error',String(error&&error.message||'Custom EPUB reader font failed to load'));
  });
})();
